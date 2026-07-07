package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.WebSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Live STT via Deepgram's streaming WebSocket ({@code wss://api.deepgram.com/v1/listen}). Audio is sent
 * as binary frames; Deepgram returns {@code Results} messages with interim and {@code is_final} hypotheses
 * plus optional diarization. Selected by {@code reqsai.ai.stt.streaming.provider=deepgram}.
 *
 */
@Slf4j
public class DeepgramStreamingAdapter extends AbstractWebSocketStreamingAdapter {

    private final String apiKey;
    private final String model;

    public DeepgramStreamingAdapter(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    protected String provider() {
        return "deepgram";
    }

    @Override
    protected void guardConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("DEEPGRAM_API_KEY is not set — cannot open a Deepgram streaming session");
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
    }

    @Override
    protected URI endpoint(Context context) {
        String language = context.language() != null ? context.language() : "es";
        // Deepgram streaming (nova-2) does not support region-specific Spanish codes like es-PE.
        // Map any regional Spanish (e.g. es-PE, es-ES) to the base 'es' tag, keeping 'es-419'.
        if (language.startsWith("es-") && !language.equals("es-419")) {
            language = "es";
        }
        return URI.create("wss://api.deepgram.com/v1/listen"
                + "?model=" + URLEncoder.encode(model, StandardCharsets.UTF_8)
                + "&language=" + URLEncoder.encode(language, StandardCharsets.UTF_8)
                + "&encoding=linear16&sample_rate=16000&channels=1"
                + "&punctuate=true&interim_results=true&diarize=true"
                + "&endpointing=300");
    }

    @Override
    protected void applyHeaders(WebSocket.Builder builder) {
        builder.header("Authorization", "Token " + apiKey);
    }

    @Override
    protected void parseFrame(String json, Listener listener) throws Exception {
        JsonNode root = JSON.readTree(json);
        if (!"Results".equals(root.path("type").asText())) {
            return; // Metadata / SpeechStarted / UtteranceEnd — ignore
        }
        JsonNode alt = root.path("channel").path("alternatives").path(0);
        String text = alt.path("transcript").asText("");
        if (text.isBlank()) {
            return;
        }
        boolean isFinal = root.path("is_final").asBoolean(false);
        double start = root.path("start").asDouble(0);
        double end = start + root.path("duration").asDouble(0);
        String speaker = null;
        JsonNode firstWord = alt.path("words").path(0);
        if (firstWord.has("speaker")) {
            speaker = String.valueOf(firstWord.path("speaker").asInt());
        }
        listener.onTranscript(new TranscriptEvent(text, speaker, Math.round(start * 1000), Math.round(end * 1000), isFinal));
    }
}
