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
 * Transcribes the uploaded audio via {@link TranscriptionPort} and saves the result in the session,
 * transitioning it from {@code DRAFT} to {@code STOPPED}. The session's meeting language is passed as a
 * hint ({@code es-PE} → {@code es}), so the provider does not have to guess it from the audio.
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

        var result = transcription.transcribe(
                command.audioBytes(), command.filename(), session.getLanguage().primaryLanguage());

        session.uploadTranscript(result.text(), result.durationMs());
        DiscoverySession saved = sessions.save(session);
        log.info("Transcript uploaded for session {} ({} chars, {}ms) — status STOPPED",
                saved.getId(), result.text().length(), result.durationMs());
        return saved;
    }
}
