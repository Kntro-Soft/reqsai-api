package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository.SessionStats;
import com.kntro.reqsai.discovery.application.query.GetProjectSessionQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetProjectSessionQueryHandler {

    private final DiscoverySessionRepository sessions;
    private final SessionStatsRepository stats;

    @Transactional(readOnly = true)
    public SessionWithStats handle(GetProjectSessionQuery query) {
        DiscoverySession session = sessions.findById(query.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));
        if (!session.getProjectId().equals(query.projectId())) {
            throw DiscoveryExceptions.sessionNotFound(query.sessionId());
        }
        SessionStats sessionStats = stats.statsForSessions(List.of(session.getId()))
                .getOrDefault(session.getId(), SessionStats.zero());
        return new SessionWithStats(session, sessionStats);
    }
}
