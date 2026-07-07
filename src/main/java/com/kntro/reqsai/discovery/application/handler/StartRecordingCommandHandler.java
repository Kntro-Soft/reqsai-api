package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StartRecordingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles transitioning a discovery session status to RECORDING.
 *
 * <p>Enforces the single-active-session rule: at most one session per project may be live
 * ({@code RECORDING} or {@code PAUSED}) at a time. The check runs inside the transaction; the
 * partial unique index {@code uq_sessions_project_active} is the concurrency backstop, so two
 * simultaneous starts cannot both win even if their reads interleave.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartRecordingCommandHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional
    public DiscoverySession handle(StartRecordingCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        sessions.findActiveByProjectId(command.projectId())
                .filter(active -> !active.getId().equals(session.getId()))
                .ifPresent(active -> {
                    throw DiscoveryExceptions.sessionAlreadyActive(command.projectId(), active.getId());
                });

        session.startRecording(Instant.now());
        DiscoverySession saved = sessions.save(session);

        log.info("Discovery session {} started recording for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
