package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.ListSessionStoriesQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListSessionStoriesQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "title", "priority", "status", "createdAt");

    private final DiscoverySessionRepository sessions;
    private final UserStoryRepository stories;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<UserStory> handle(ListSessionStoriesQuery query) {
        sessions.findById(query.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));
        return stories.findAllBySessionId(
                query.sessionId(),
                pageRequestFactory.toPageable(query.criteria(), SORT));
    }
}
