package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ResumeRecordingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles resuming a paused recording back to RECORDING.
 *
 * <p>Enforces the single-active-session rule: a resume is rejected when a <em>different</em> session
 * of the same project is already live ({@code RECORDING} or {@code PAUSED}). The resuming session is
 * itself PAUSED (hence "active"), so it is excluded from the check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResumeRecordingCommandHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional
    public DiscoverySession handle(ResumeRecordingCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        sessions.findActiveByProjectId(command.projectId())
                .filter(active -> !active.getId().equals(session.getId()))
                .ifPresent(active -> {
                    throw DiscoveryExceptions.sessionAlreadyActive(command.projectId(), active.getId());
                });

        session.resumeRecording();
        DiscoverySession saved = sessions.save(session);

        log.info("Discovery session {} resumed recording for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
