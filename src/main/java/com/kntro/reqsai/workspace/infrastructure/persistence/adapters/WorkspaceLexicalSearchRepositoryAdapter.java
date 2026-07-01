package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.shared.application.search.SearchHitType;
import com.kntro.reqsai.workspace.application.port.WorkspaceLexicalSearchRepository;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.MemberSearchJpaRepository;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.OrganizationSearchJpaRepository;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectSearchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adapter over the pg_trgm native search repositories. Maps {@code Object[]} rows to {@link SearchHit}
 * value snapshots. Scoping (which org/project ids) is decided upstream and passed in as concrete ids.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceLexicalSearchRepositoryAdapter implements WorkspaceLexicalSearchRepository {

    private final ProjectSearchJpaRepository projectSearch;
    private final OrganizationSearchJpaRepository organizationSearch;
    private final MemberSearchJpaRepository memberSearch;

    @Override
    public List<SearchHit> searchProjectsInOrganization(UUID organizationId, String term, int limit) {
        return projectSearch.searchByOrganization(organizationId, term, PageRequest.of(0, limit)).stream()
                .map(WorkspaceLexicalSearchRepositoryAdapter::toProjectHit)
                .toList();
    }

    @Override
    public List<SearchHit> searchProjectsInIds(UUID organizationId, Collection<UUID> projectIds, String term, int limit) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return projectSearch.searchByOrganizationAndIdIn(organizationId, projectIds, term, PageRequest.of(0, limit)).stream()
                .map(WorkspaceLexicalSearchRepositoryAdapter::toProjectHit)
                .toList();
    }

    @Override
    public List<SearchHit> searchOrganizations(Collection<UUID> organizationIds, String term, int limit) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        return organizationSearch.searchWithinIds(organizationIds, term, PageRequest.of(0, limit)).stream()
                .map(row -> {
                    UUID id = UUID.fromString(row[0].toString());
                    String name = (String) row[1];
                    String slug = (String) row[2];
                    return new SearchHit(SearchHitType.ORGANIZATION, id, name, slug, null);
                })
                .toList();
    }

    @Override
    public List<SearchHit> searchMembers(UUID organizationId, String term, int limit) {
        return memberSearch.searchByOrganization(organizationId, term, PageRequest.of(0, limit)).stream()
                .map(row -> {
                    UUID id = UUID.fromString(row[0].toString());
                    String displayName = (String) row[1];
                    String email = (String) row[2];
                    return new SearchHit(SearchHitType.MEMBER, id, displayName, email, null);
                })
                .toList();
    }

    private static SearchHit toProjectHit(Object[] row) {
        UUID id = UUID.fromString(row[0].toString());
        String name = (String) row[1];
        return new SearchHit(SearchHitType.PROJECT, id, name, null, id);
    }
}
