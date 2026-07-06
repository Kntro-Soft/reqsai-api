package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.ListProjectStoriesQuery;
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
public class ListProjectStoriesQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "title", "priority", "status", "createdAt");

    private final UserStoryRepository stories;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<UserStory> handle(ListProjectStoriesQuery query) {
        return stories.findAllByProjectId(
                query.projectId(),
                query.filter(),
                pageRequestFactory.toPageable(query.criteria(), SORT));
    }
}
