package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StopRecordingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles transitioning a discovery session to STOPPED status.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StopRecordingCommandHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional
    public DiscoverySession handle(StopRecordingCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        session.stopRecording();
        DiscoverySession saved = sessions.save(session);

        log.info("Discovery session {} stopped recording for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
