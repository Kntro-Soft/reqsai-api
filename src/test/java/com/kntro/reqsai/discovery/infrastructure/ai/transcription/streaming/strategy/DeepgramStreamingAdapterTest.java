package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort.TranscriptEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing of Deepgram streaming {@code Results} frames with diarization (US40): a final result whose words
 * change speaker becomes one final event per speaker turn.
 */
@DisplayName("Infra: Deepgram streaming diarization")
class DeepgramStreamingAdapterTest {

    private final DeepgramStreamingAdapter adapter = new DeepgramStreamingAdapter("key", "nova-2");
    private final List<TranscriptEvent> events = new ArrayList<>();

    private static String word(String word, String punctuated, double start, double end, int speaker) {
        return """
                {"word":"%s","punctuated_word":"%s","start":%s,"end":%s,"speaker":%d}"""
                .formatted(word, punctuated, start, end, speaker);
    }

    private static String results(boolean isFinal, String transcript, double start, double duration, String... words) {
        return """
                {"type":"Results","is_final":%s,"start":%s,"duration":%s,
                 "channel":{"alternatives":[{"transcript":"%s","words":[%s]}]}}"""
                .formatted(isFinal, start, duration, transcript, String.join(",", words));
    }

    @Test
    @DisplayName("requests diarization on the streaming endpoint")
    void requests_diarization() {
        URI uri = adapter.endpoint(new com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort.Context(
                UUID.randomUUID(), "es-PE"));

        assertThat(uri.getQuery()).contains("diarize=true").contains("language=es");
    }

    @Test
    @DisplayName("splits a final result into one final event per speaker turn, with each turn's timing")
    void splits_final_by_speaker() throws Exception {
        adapter.parseFrame(results(true, "¿Para cuántas personas? Para cuatro.", 10.0, 3.0,
                word("para", "¿Para", 10.0, 10.3, 1),
                word("cuántas", "cuántas", 10.3, 10.7, 1),
                word("personas", "personas?", 10.7, 11.2, 1),
                word("para", "Para", 11.6, 11.9, 0),
                word("cuatro", "cuatro.", 11.9, 12.5, 0)), events::add);

        assertThat(events).containsExactly(
                new TranscriptEvent("¿Para cuántas personas?", "1", 10_000, 11_200, true),
                new TranscriptEvent("Para cuatro.", "0", 11_600, 12_500, true));
    }

    @Test
    @DisplayName("keeps a single-speaker final as one event with the provider's transcript")
    void keeps_single_speaker_final() throws Exception {
        adapter.parseFrame(results(true, "Quiero reservar.", 0.0, 2.0,
                word("quiero", "Quiero", 0.1, 0.5, 0),
                word("reservar", "reservar.", 0.5, 1.2, 0)), events::add);

        assertThat(events).containsExactly(new TranscriptEvent("Quiero reservar.", "0", 0, 2_000, true));
    }

    @Test
    @DisplayName("does not split an interim result; it carries the first word's speaker")
    void interim_is_not_split() throws Exception {
        adapter.parseFrame(results(false, "hola sí", 0.0, 1.0,
                word("hola", "hola", 0.0, 0.4, 0),
                word("sí", "sí", 0.5, 0.8, 1)), events::add);

        assertThat(events).containsExactly(new TranscriptEvent("hola sí", "0", 0, 1_000, false));
    }

    @Test
    @DisplayName("keeps a final without speaker labels whole, with no speaker")
    void final_without_labels() throws Exception {
        adapter.parseFrame("""
                {"type":"Results","is_final":true,"start":1.0,"duration":1.0,
                 "channel":{"alternatives":[{"transcript":"hola","words":[{"word":"hola","start":1.0,"end":1.5}]}]}}""",
                events::add);

        assertThat(events).containsExactly(new TranscriptEvent("hola", null, 1_000, 2_000, true));
    }
}
