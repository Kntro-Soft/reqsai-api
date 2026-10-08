package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.WebSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Live STT via Deepgram's streaming WebSocket ({@code wss://api.deepgram.com/v1/listen}). Audio is sent
 * as binary frames; Deepgram returns {@code Results} messages with interim and {@code is_final} hypotheses
 * plus optional diarization. Selected by {@code reqsai.ai.stt.streaming.provider=deepgram}.
 *
 * <p>Diarization ({@code diarize=true}) labels every word with a speaker ("0", "1", …). A final result whose
 * words change speaker is emitted as one final event per speaker turn ({@link #speakerTurns}).
 *
 * <p>Deepgram closes a stream that receives no audio for 10 s (error {@code NET-0001}); a
 * {@code {"type":"KeepAlive"}} text frame resets that window, so an idle stream sends one every few
 * seconds (see {@link StreamResilience}).
 */
@Slf4j
public class DeepgramStreamingAdapter extends AbstractWebSocketStreamingAdapter {

    private static final String KEEP_ALIVE = "{\"type\":\"KeepAlive\"}";

    private final String apiKey;
    private final String model;

    public DeepgramStreamingAdapter(String apiKey, String model) {
        this(apiKey, model, StreamResilience.defaults());
    }

    DeepgramStreamingAdapter(String apiKey, String model, StreamResilience resilience) {
        super(resilience);
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
    protected String keepAliveMessage() {
        return KEEP_ALIVE;
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
        JsonNode words = alt.path("words");
        if (isFinal) {
            List<TranscriptEvent> turns = speakerTurns(words);
            if (turns.size() > 1) {
                turns.forEach(listener::onTranscript);
                return;
            }
        }
        String speaker = null;
        JsonNode firstWord = words.path(0);
        if (firstWord.has("speaker")) {
            speaker = String.valueOf(firstWord.path("speaker").asInt());
        }
        listener.onTranscript(new TranscriptEvent(text, speaker, Math.round(start * 1000), Math.round(end * 1000), isFinal));
    }

    /**
     * Splits a final result into one event per speaker turn when its diarized words change speaker, so each
     * part is attributed to whoever said it instead of the whole result going to the first word's speaker
     * (a quick question and answer often land in one result). Each turn keeps its words' own timing and
     * punctuated text. Returns a single element or none when the words carry one speaker or no labels.
     */
    static List<TranscriptEvent> speakerTurns(JsonNode words) {
        List<TranscriptEvent> turns = new ArrayList<>();
        if (!words.isArray() || words.isEmpty()) {
            return turns;
        }
        StringBuilder text = new StringBuilder();
        String speaker = null;
        double turnStart = 0;
        double turnEnd = 0;
        for (JsonNode word : words) {
            if (!word.has("speaker")) {
                return List.of();
            }
            String wordSpeaker = String.valueOf(word.path("speaker").asInt());
            String token = word.path("punctuated_word").asText(word.path("word").asText("")).strip();
            if (speaker != null && !speaker.equals(wordSpeaker)) {
                addTurn(turns, text, speaker, turnStart, turnEnd);
                text.setLength(0);
            }
            if (text.isEmpty()) {
                speaker = wordSpeaker;
                turnStart = word.path("start").asDouble(0);
            }
            if (!token.isEmpty()) {
                text.append(text.isEmpty() ? "" : " ").append(token);
            }
            turnEnd = Math.max(turnStart, word.path("end").asDouble(turnStart));
        }
        addTurn(turns, text, speaker, turnStart, turnEnd);
        return turns;
    }

    private static void addTurn(List<TranscriptEvent> turns, StringBuilder text, String speaker, double start, double end) {
        if (!text.isEmpty()) {
            turns.add(new TranscriptEvent(text.toString(), speaker, Math.round(start * 1000), Math.round(end * 1000), true));
        }
    }
}
