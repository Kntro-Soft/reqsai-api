package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.DiscoveryLexicalSearchRepository;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.UserStorySearchJpaRepository;
import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.shared.application.search.SearchHitType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Adapter over the pg_trgm native story-search repository. Maps {@code Object[]} rows to {@link SearchHit}. */
@Component
@RequiredArgsConstructor
public class DiscoveryLexicalSearchRepositoryAdapter implements DiscoveryLexicalSearchRepository {

    private final UserStorySearchJpaRepository storySearch;

    @Override
    public List<SearchHit> searchAllUserStories(String term, int limit) {
        return storySearch.searchAll(term, PageRequest.of(0, limit)).stream()
                .map(DiscoveryLexicalSearchRepositoryAdapter::toStoryHit)
                .toList();
    }

    @Override
    public List<SearchHit> searchUserStoriesInProjects(Collection<UUID> projectIds, String term, int limit) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return storySearch.searchInProjects(projectIds, term, PageRequest.of(0, limit)).stream()
                .map(DiscoveryLexicalSearchRepositoryAdapter::toStoryHit)
                .toList();
    }

    private static SearchHit toStoryHit(Object[] row) {
        UUID id = UUID.fromString(row[0].toString());
        String title = (String) row[1];
        UUID projectId = UUID.fromString(row[2].toString());
        return new SearchHit(SearchHitType.USER_STORY, id, title, null, projectId);
    }
}
