package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoveryLexicalSearchRepository;
import com.kntro.reqsai.discovery.search.DiscoverySearchPort;
import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Discovery implementation of the {@code discovery::search} named interface. Filters story results to
 * the {@link ProjectScope} resolved by the workspace access rules, so a caller never sees stories from
 * projects they cannot access.
 */
@Component
@RequiredArgsConstructor
class DiscoverySearchPortImpl implements DiscoverySearchPort {

    private final DiscoveryLexicalSearchRepository lexicalSearch;

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> searchUserStories(String term, int limit, ProjectScope scope) {
        if (scope.isEmpty()) {
            return List.of();
        }
        if (scope.all()) {
            return lexicalSearch.searchAllUserStories(term, limit);
        }
        return lexicalSearch.searchUserStoriesInProjects(scope.projectIds(), term, limit);
    }
}
