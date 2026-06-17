package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ResetSessionCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles resetting a discovery session, clearing its transcript data and removing all
 * user stories previously extracted from it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResetSessionCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final UserStoryRepository userStories;

    @Transactional
    public DiscoverySession handle(ResetSessionCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        // Delete all user stories previously extracted from this session
        userStories.deleteAllBySessionId(command.sessionId());

        // Perform domain reset
        session.resetSession();
        DiscoverySession saved = sessions.save(session);

        log.info("Discovery session {} reset back to DRAFT for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
