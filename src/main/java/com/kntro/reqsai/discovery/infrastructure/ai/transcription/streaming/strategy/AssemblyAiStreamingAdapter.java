package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.TimeUnit;

/**
 * Live STT via AssemblyAI Universal-Streaming v3 ({@code wss://streaming.assemblyai.com/v3/ws}). Audio is
 * sent as binary PCM16 frames; AssemblyAI returns immutable {@code Turn} messages — a turn with
 * {@code end_of_turn=true} is treated as a final segment. Selected by
 * {@code reqsai.ai.stt.streaming.provider=assemblyai}.
 *
 */
@Slf4j
public class AssemblyAiStreamingAdapter extends AbstractWebSocketStreamingAdapter {

    private static final int SAMPLE_RATE = 16_000;

    private final String apiKey;

    public AssemblyAiStreamingAdapter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected String provider() {
        return "assemblyai";
    }

    @Override
    protected void guardConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("ASSEMBLYAI_API_KEY is not set — cannot open an AssemblyAI streaming session");
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
    }

    @Override
    protected URI endpoint(Context context) {
        // end_of_turn_confidence_threshold=0.4 — fires turn more aggressively (default ~0.7).
        // max_turn_silence_ms=700 — forces end_of_turn after 700ms of silence regardless of confidence.
        // Both together produce multiple segments for natural speech; the Terminate flush still handles
        // any remaining partial at close time.
        return URI.create("wss://streaming.assemblyai.com/v3/ws"
                + "?sample_rate=" + SAMPLE_RATE
                + "&format_turns=true"
                + "&end_of_turn_confidence_threshold=0.4"
                + "&max_turn_silence_ms=700");
    }

    @Override
    protected void applyHeaders(WebSocket.Builder builder) {
        builder.header("Authorization", apiKey);
    }

    @Override
    protected void beforeClose(WebSocket socket) {
        // Signal AssemblyAI to finalize and flush any pending turn before closing.
        // Without this, an in-progress turn never fires end_of_turn=true and is lost.
        try {
            socket.sendText("{\"type\":\"Terminate\"}", true)
                    .get(3, TimeUnit.SECONDS);
            // Give AssemblyAI time to return the final Turn message before the WS close frame goes out.
            Thread.sleep(2_000);
        } catch (Exception e) {
            log.debug("AssemblyAI Terminate flush skipped: {}", e.getMessage());
        }
    }

    @Override
    protected void parseFrame(String json, Listener listener) throws Exception {
        JsonNode root = JSON.readTree(json);
        if (!"Turn".equals(root.path("type").asText())) {
            return; // Begin / Termination — ignore
        }
        String text = root.path("transcript").asText("");
        if (text.isBlank()) {
            return;
        }
        boolean isFinal = root.path("end_of_turn").asBoolean(false);
        JsonNode words = root.path("words");
        long startMs = 0;
        long endMs = 0;
        String speaker = null;
        if (words.isArray() && !words.isEmpty()) {
            JsonNode firstWord = words.get(0);
            startMs = firstWord.path("start").asLong(0);
            endMs = words.get(words.size() - 1).path("end").asLong(0);
            if (firstWord.has("speaker")) {
                speaker = String.valueOf(firstWord.path("speaker").asInt());
            }
        }
        listener.onTranscript(new TranscriptEvent(text, speaker, startMs, endMs, isFinal));
    }
}
