package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository.SessionStats;
import com.kntro.reqsai.discovery.application.query.ListProjectSessionsQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListProjectSessionsQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "title", "status", "createdAt");

    private final DiscoverySessionRepository sessions;
    private final SessionStatsRepository stats;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<SessionWithStats> handle(ListProjectSessionsQuery query) {
        Page<DiscoverySession> page = sessions.findAllByProjectId(
                query.projectId(),
                pageRequestFactory.toPageable(query.criteria(), SORT));

        // One grouped query for the whole page — no N+1.
        List<UUID> ids = page.getContent().stream().map(DiscoverySession::getId).toList();
        Map<UUID, SessionStats> statsById = stats.statsForSessions(ids);

        return page.map(session ->
                new SessionWithStats(session, statsById.getOrDefault(session.getId(), SessionStats.zero())));
    }
}
