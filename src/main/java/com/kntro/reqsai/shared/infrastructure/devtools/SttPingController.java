package com.kntro.reqsai.shared.infrastructure.devtools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Dev-only smoke check for Speech-to-Text (Whisper, via the OpenAI-compatible adapter). Kept separate
 * from {@link AiPingController} because it needs a different bean ({@link TranscriptionModel}) that only
 * exists when STT is selected ({@code spring.ai.model.audio.transcription=openai}) — so the chat/embedding
 * ping stays testable on its own. Active under the {@code local-ai} profile and only when STT is on.
 * Throwaway scaffolding; {@code discovery} replaces it. Requires a JWT (mint one via
 * {@code /api/v1/auth/dev-token}).
 * <p>
 * {@code POST /api/v1/ai/transcribe} (multipart {@code file}) → returns the transcript.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Profile("local-ai")
@ConditionalOnProperty(name = "spring.ai.model.audio.transcription", havingValue = "openai")
@RequiredArgsConstructor
@Slf4j
public class SttPingController {

    private final TranscriptionModel transcriptionModel;

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> transcribe(@RequestParam("file") MultipartFile file) {
        try {
            String transcript = transcriptionModel
                    .call(new AudioTranscriptionPrompt(file.getResource()))
                    .getResult()
                    .getOutput();
            return Map.of(
                    "ok", true,
                    "fileName", String.valueOf(file.getOriginalFilename()),
                    "transcript", transcript);
        } catch (Exception e) {
            log.warn("STT transcribe failed", e);
            return Map.of(
                    "ok", false,
                    "error", "Transcription failed — is Whisper reachable at the configured base-url? "
                            + "(compose `ai` profile exposes it on :9000)",
                    "detail", String.valueOf(e.getMessage()));
        }
    }
}
