package com.kntro.reqsai.discovery.infrastructure.ai.transcription.batch.strategy;

import com.deepgram.DeepgramClient;
import com.deepgram.resources.listen.v1.media.requests.MediaTranscribeRequestOctetStream;
import com.deepgram.resources.listen.v1.media.types.MediaTranscribeResponse;
import com.deepgram.types.ListenV1Response;
import com.deepgram.types.ListenV1ResponseResultsUtterancesItem;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * STT adapter backed by the official Deepgram Java SDK. Submits pre-recorded audio with
 * speaker diarization and returns full transcript + per-utterance {@link TranscriptionResult.SpeakerSegment}s
 * (the speaker labels "0", "1", …).
 *
 * <p>Configure via {@code DEEPGRAM_API_KEY} env var and set {@code STT_PROVIDER=deepgram}.
 *
 * <p>Not a Spring bean — instantiated and held by {@code SttRouter}.
 */
@Slf4j
public class DeepgramAdapter {

    private final String apiKey;

    public DeepgramAdapter(String apiKey) {
        this.apiKey = apiKey;
    }

    public TranscriptionResult transcribe(byte[] audio, String filename) {
        if (apiKey == null || apiKey.isBlank()) {
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }

        log.debug("Sending {} bytes to Deepgram SDK (file={})", audio.length, filename);

        DeepgramClient deepgram = DeepgramClient.builder()
                .apiKey(apiKey)
                .build();

        MediaTranscribeRequestOctetStream request = MediaTranscribeRequestOctetStream.builder()
                .body(audio)
                .diarize(true)
                .punctuate(true)
                .detectLanguage(true)
                .build();

        MediaTranscribeResponse response = deepgram.listen().v1().media().transcribeFile(request);

        return response.visit(new MediaTranscribeResponse.Visitor<>() {

            @Override
            public TranscriptionResult visit(ListenV1Response value) {
                return mapResponse(value);
            }

            @Override
            public TranscriptionResult visit(com.deepgram.types.ListenV1AcceptedResponse value) {
                // Async acceptance — should not occur for synchronous pre-recorded calls
                log.warn("Deepgram returned async acceptance for a synchronous request");
                throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
            }
        });
    }

    private TranscriptionResult mapResponse(ListenV1Response value) {
        var results = value.getResults();

        String text = "";
        String language = null;
        double confidence = 0.0;

        if (results != null && !results.getChannels().isEmpty()) {
            var channel = results.getChannels().getFirst();
            language = channel.getDetectedLanguage().orElse(null);
            var alts = channel.getAlternatives().orElse(List.of());
            if (!alts.isEmpty()) {
                var alt = alts.getFirst();
                text = alt.getTranscript().orElse("");
                confidence = alt.getConfidence().map(f -> (double) f).orElse(0.0);
            }
        }

        long durationMs = value.getMetadata() != null
                ? (long) (value.getMetadata().getDuration() * 1000) : 0L;

        List<TranscriptionResult.SpeakerSegment> segments = null;
        if (results != null) {
            segments = results.getUtterances()
                    .map(list -> list.stream().map(this::toSegment).toList())
                    .orElse(null);
        }

        log.debug("Deepgram transcribed {} chars, {} utterances", text.length(), segments != null ? segments.size() : 0);
        if (text.isBlank()) {
            throw DiscoveryInfrastructureExceptions.transcriptionFailed("Deepgram returned an empty transcript");
        }
        return new TranscriptionResult(text, language, durationMs, confidence, segments);
    }

    private TranscriptionResult.SpeakerSegment toSegment(ListenV1ResponseResultsUtterancesItem u) {
        String speaker = u.getSpeaker().map(String::valueOf).orElse(null);
        String text = u.getTranscript().orElse("");
        long startMs = u.getStart().map(s -> (long) (s * 1000)).orElse(0L);
        long endMs = u.getEnd().map(e -> (long) (e * 1000)).orElse(0L);
        Double confidence = u.getConfidence().map(Double::valueOf).orElse(null);
        return new TranscriptionResult.SpeakerSegment(speaker, text, startMs, endMs, confidence);
    }
}
