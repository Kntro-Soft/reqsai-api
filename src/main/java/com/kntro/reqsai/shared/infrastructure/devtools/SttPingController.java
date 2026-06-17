package com.kntro.reqsai.shared.infrastructure.devtools;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Dev-only smoke check for Speech-to-Text. Always active under {@code dev}; reports {@code ok:false}
 * if no STT provider is configured ({@code SPRING_AI_MODEL_AUDIO_TRANSCRIPTION} not set), so it
 * degrades gracefully. Kept separate from {@link AiPingController} because it needs a different bean
 * ({@link TranscriptionModel}) that only exists when STT is enabled. Requires a JWT.
 * <p>
 * {@code POST /api/ai/transcribe} (multipart {@code file}) → returns the transcript.
 */
@RestController
@RequestMapping("/api/ai")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class SttPingController {

    private final ObjectProvider<TranscriptionModel> transcriptionModelProvider;

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, version = ApiVersioning.V1)
    public Map<String, Object> transcribe(@RequestParam("file") MultipartFile file) {
        TranscriptionModel transcriptionModel = transcriptionModelProvider.getIfAvailable();

        if (transcriptionModel == null) {
            return Map.of(
                    "ok", false,
                    "reason", "STT not configured — set SPRING_AI_MODEL_AUDIO_TRANSCRIPTION=openai "
                            + "and WHISPER_BASE_URL (or OPENAI_API_KEY) in your .env");
        }

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
                    "error", "Transcription failed — is the STT provider reachable? "
                            + "Check WHISPER_BASE_URL or OPENAI_API_KEY in your .env",
                    "detail", String.valueOf(e.getMessage()));
        }
    }
}
