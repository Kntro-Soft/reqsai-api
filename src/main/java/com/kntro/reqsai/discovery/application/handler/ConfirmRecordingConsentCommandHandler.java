package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ConfirmRecordingConsentCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records the analyst's confirmation that the participants consented to the recording (Ley N.° 29733),
 * with the confirming user and the date, so the live capture can start.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfirmRecordingConsentCommandHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional
    public DiscoverySession handle(ConfirmRecordingConsentCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        session.confirmRecordingConsent(command.confirmedBy(), Instant.now());
        DiscoverySession saved = sessions.save(session);

        log.info("Recording consent confirmed for session {} of project {} by user {}",
                saved.getId(), command.projectId(), command.confirmedBy());
        return saved;
    }
}
