package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UploadTranscriptCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transcribes the uploaded audio via {@link TranscriptionPort} (Whisper) and saves the result in
 * the session, transitioning it from {@code DRAFT} to {@code STOPPED}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadTranscriptCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final TranscriptionPort transcription;

    @Transactional
    public DiscoverySession handle(UploadTranscriptCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        var result = transcription.transcribe(command.audioBytes(), command.filename());

        session.uploadTranscript(result.text(), result.durationMs());
        DiscoverySession saved = sessions.save(session);
        log.info("Transcript uploaded for session {} ({} chars, {}ms) — status STOPPED",
                saved.getId(), result.text().length(), result.durationMs());
        return saved;
    }
}
