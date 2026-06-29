package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ResetDiscoverySessionCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles transitioning a DiscoverySession back to the DRAFT state, which is the initial state of a session.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResetDiscoverySessionCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final UserStoryRepository userStories;
    private final SuggestionRepository suggestions;
    private final TranscriptSegmentRepository transcriptSegments;

    @Transactional
    public DiscoverySession handle(ResetDiscoverySessionCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

       userStories.deleteAllBySessionId(command.sessionId());
        suggestions.deleteAllBySessionId(command.sessionId());
        transcriptSegments.deleteAllBySessionId(command.sessionId());
        session.reset();
        DiscoverySession saved = sessions.save(session);

        log.info("Discovery session {} reset to DRAFT for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
