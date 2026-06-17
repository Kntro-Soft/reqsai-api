package com.kntro.reqsai.discovery.infrastructure.ai.transcription.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * STT adapter for AssemblyAI's async pre-recorded API with speaker diarization.
 * Implements the three-step async flow: (1) upload audio bytes → upload_url;
 * (2) submit transcription job with speaker_labels=true; (3) poll until completed.
 *
 * <p>Speaker labels are "A", "B", "C", … (AssemblyAI convention).
 * Configure via {@code ASSEMBLYAI_API_KEY} and set {@code STT_PROVIDER=assemblyai}.
 *
 * <p>Not a Spring bean — instantiated and held by {@code SttRouter}.
 */
@Slf4j
public class AssemblyAiAdapter {

    private static final String BASE_URL = "https://api.assemblyai.com/v2";
    private static final int MAX_POLLS = 60;
    private static final long POLL_DELAY_MS = 3_000;

    private final RestClient restClient;
    private final String apiKey;

    public AssemblyAiAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public TranscriptionResult transcribe(byte[] audio, String filename) {
        if (apiKey == null || apiKey.isBlank()) {
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
        String audioUrl = uploadAudio(audio, filename);
        String jobId = submitJob(audioUrl);
        TranscriptJob completed = pollUntilDone(jobId);
        guardJobError(completed, jobId);
        return buildResult(completed);
    }

    private String uploadAudio(byte[] audio, String filename) {
        log.debug("Uploading {} bytes to AssemblyAI (file={})", audio.length, filename);
        UploadResponse upload = restClient.post()
                .uri(BASE_URL + "/upload")
                .header("Authorization", apiKey)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(audio)
                .retrieve()
                .body(UploadResponse.class);
        if (upload == null || upload.uploadUrl() == null) {
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
        return upload.uploadUrl();
    }

    private String submitJob(String audioUrl) {
        log.debug("Submitting AssemblyAI job for upload_url={}", audioUrl);
        TranscriptJob job = restClient.post()
                .uri(BASE_URL + "/transcript")
                .header("Authorization", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("audio_url", audioUrl, "speaker_labels", true))
                .retrieve()
                .body(TranscriptJob.class);
        if (job == null || job.id() == null) {
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
        return job.id();
    }

    private TranscriptJob pollUntilDone(String jobId) {
        for (int attempt = 1; attempt <= MAX_POLLS; attempt++) {
            TranscriptJob job = restClient.get()
                    .uri(BASE_URL + "/transcript/" + jobId)
                    .header("Authorization", apiKey)
                    .retrieve()
                    .body(TranscriptJob.class);
            if (job == null) {
                throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
            }
            log.debug("AssemblyAI poll {}/{}: status={}", attempt, MAX_POLLS, job.status());
            if ("completed".equals(job.status()) || "error".equals(job.status())) return job;
            sleepOrInterrupt();
        }
        log.error("AssemblyAI job {} timed out after {} polls", jobId, MAX_POLLS);
        throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
    }

    private void sleepOrInterrupt() {
        try {
            Thread.sleep(POLL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DiscoveryInfrastructureExceptions.transcriptionFailed("Polling was interrupted", e);
        }
    }

    private void guardJobError(TranscriptJob job, String jobId) {
        if ("error".equals(job.status())) {
            String reason = job.error() != null ? job.error() : "AssemblyAI job failed without a reason";
            log.error("AssemblyAI job {} failed: {}", jobId, reason);
            throw DiscoveryInfrastructureExceptions.transcriptionFailed(reason);
        }
    }

    private TranscriptionResult buildResult(TranscriptJob job) {
        String text = job.text() != null ? job.text() : "";
        long durationMs = job.audioDuration() != null ? (long) (job.audioDuration() * 1000) : 0L;
        List<TranscriptionResult.SpeakerSegment> segments = extractSegments(job);
        log.debug("AssemblyAI transcribed {} chars, {} utterances", text.length(), segments != null ? segments.size() : 0);
        if (text.isBlank()) {
            throw DiscoveryInfrastructureExceptions.transcriptionFailed("AssemblyAI returned an empty transcript");
        }
        return new TranscriptionResult(text, job.languageCode(), durationMs, job.confidence(), segments);
    }

    private List<TranscriptionResult.SpeakerSegment> extractSegments(TranscriptJob job) {
        if (job.utterances() == null) return null;
        return job.utterances().stream()
                .map(u -> new TranscriptionResult.SpeakerSegment(u.speaker(), u.text(), u.start(), u.end(), u.confidence()))
                .toList();
    }

    // Jackson-bound response records

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UploadResponse(@JsonProperty("upload_url") String uploadUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TranscriptJob(
            String id,
            String status,
            String text,
            String error,
            Double confidence,
            @JsonProperty("language_code") String languageCode,
            @JsonProperty("audio_duration") Double audioDuration,
            List<Utterance> utterances) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Utterance(String speaker, String text, long start, long end, Double confidence) {}
}
