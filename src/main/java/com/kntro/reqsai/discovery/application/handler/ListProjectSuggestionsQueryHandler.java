package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.service.ResolvedStoryStatusReader;
import com.kntro.reqsai.discovery.application.query.ListProjectSuggestionsQuery;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists a project's suggestions filtered by review status (default {@code PENDING}), paginated — the
 * "pending from previous sessions" backlog-triage view.
 */
@Component
@RequiredArgsConstructor
public class ListProjectSuggestionsQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "createdAt");

    private final SuggestionRepository suggestions;
    private final PageRequestFactory pageRequestFactory;
    private final ResolvedStoryStatusReader storyStatuses;

    @Transactional(readOnly = true)
    public Page<Suggestion> handle(ListProjectSuggestionsQuery query) {
        Page<Suggestion> page = suggestions.findAllByProjectIdAndStatus(
                query.projectId(),
                query.statusOrDefault(),
                pageRequestFactory.toPageable(query.criteria(), SORT));
        storyStatuses.attach(query.projectId(), page.getContent());
        return page;
    }
}
