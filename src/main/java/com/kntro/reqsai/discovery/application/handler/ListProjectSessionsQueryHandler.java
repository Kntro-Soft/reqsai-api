package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.query.ListProjectSessionsQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListProjectSessionsQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "title", "status", "createdAt");

    private final DiscoverySessionRepository sessions;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<DiscoverySession> handle(ListProjectSessionsQuery query) {
        return sessions.findAllByProjectId(
                query.projectId(),
                pageRequestFactory.toPageable(query.criteria(), SORT));
    }
}
