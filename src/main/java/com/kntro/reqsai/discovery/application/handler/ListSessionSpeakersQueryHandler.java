package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.query.ListSessionSpeakersQuery;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists who spoke in a session (US40): the diarized speakers by first appearance, with the names and sides
 * the analyst gave, and the stretches where two speakers talked at the same time.
 */
@Component
@RequiredArgsConstructor
public class ListSessionSpeakersQueryHandler {

    private final DiscoverySessionRepository sessions;
    private final SessionSpeakerService speakers;

    @Transactional(readOnly = true)
    public SessionSpeakerService.Overview handle(ListSessionSpeakersQuery query) {
        sessions.findById(query.sessionId())
                .filter(s -> s.getProjectId().equals(query.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));
        return speakers.overviewOf(query.sessionId());
    }
}
