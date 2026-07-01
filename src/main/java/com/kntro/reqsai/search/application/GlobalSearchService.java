package com.kntro.reqsai.search.application;

import com.kntro.reqsai.discovery.search.DiscoverySearchPort;
import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.search.WorkspaceSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global-search aggregator. Fans out a term across every bounded context's {@code search} named
 * interface, taking the top-{@code limit} per type, then merges and caps the combined list.
 *
 * <p>Runs sequentially on the request thread on purpose: there is a single connection pool and one
 * {@code search_path} bound per request, so parallel fan-out would fight over the tenant context.
 *
 * <p>The tenant (organization) is resolved from {@link TenantContext} — the same JWT {@code orgId}
 * that Hibernate uses to pick the schema — so the endpoint takes no org path variable. A blank query,
 * or a caller with no bound tenant, yields an empty result without touching the database.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    /** Absolute cap on results the caller may request. */
    public static final int MAX_LIMIT = 20;

    private final WorkspaceSearchPort workspaceSearch;
    private final DiscoverySearchPort discoverySearch;

    /**
     * Merged top matches across projects, user stories, organizations and members.
     *
     * @param term     raw query; blank/whitespace returns an empty list
     * @param limit    requested cap (clamped to {@code [1, MAX_LIMIT]}); also the per-type top-K
     * @param callerId caller's user id (JWT subject)
     */
    public List<SearchHit> search(String term, int limit, UUID callerId) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String currentTenant = TenantContext.getCurrentTenant();
        if (!StringUtils.hasText(currentTenant)) {
            return List.of();
        }
        UUID orgId = UUID.fromString(currentTenant);
        String normalized = term.strip();
        int cappedLimit = Math.clamp(limit, 1, MAX_LIMIT);

        // Resolve the caller's project scope once; both project and story searches reuse it.
        ProjectScope projectScope = workspaceSearch.resolveProjectScope(orgId, callerId);

        List<SearchHit> merged = new ArrayList<>();
        merged.addAll(workspaceSearch.searchProjects(normalized, cappedLimit, orgId, projectScope));
        merged.addAll(discoverySearch.searchUserStories(normalized, cappedLimit, projectScope));
        merged.addAll(workspaceSearch.searchOrganizations(normalized, cappedLimit, callerId));
        merged.addAll(workspaceSearch.searchMembers(normalized, cappedLimit, orgId, callerId));

        return merged.size() > cappedLimit ? merged.subList(0, cappedLimit) : merged;
    }
}
