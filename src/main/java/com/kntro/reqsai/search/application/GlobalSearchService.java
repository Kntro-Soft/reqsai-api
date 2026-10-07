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
 * interface, taking the top-{@code limit} per type, then interleaves the per-type lists (best project,
 * best story, best organization, … then the second of each) and caps the result. Interleaving keeps
 * every type that matched visible: concatenating would let a long list of stories push glossary terms
 * or members past the cap.
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
     * Merged top matches across projects, user stories, organizations, members, glossary terms and
     * documents.
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

        List<List<SearchHit>> perType = List.of(
                workspaceSearch.searchProjects(normalized, cappedLimit, orgId, projectScope),
                discoverySearch.searchUserStories(normalized, cappedLimit, projectScope),
                workspaceSearch.searchOrganizations(normalized, cappedLimit, callerId),
                workspaceSearch.searchMembers(normalized, cappedLimit, orgId, callerId),
                workspaceSearch.searchGlossaryTerms(normalized, cappedLimit, projectScope),
                workspaceSearch.searchDocuments(normalized, cappedLimit, projectScope));

        return interleave(perType, cappedLimit);
    }

    /** Round-robin over the per-type lists (each already best-first), stopping at {@code limit} hits. */
    private static List<SearchHit> interleave(List<List<SearchHit>> perType, int limit) {
        List<SearchHit> merged = new ArrayList<>(limit);
        for (int rank = 0; merged.size() < limit; rank++) {
            boolean any = false;
            for (List<SearchHit> hits : perType) {
                if (rank < hits.size()) {
                    any = true;
                    merged.add(hits.get(rank));
                    if (merged.size() == limit) {
                        return merged;
                    }
                }
            }
            if (!any) {
                break;
            }
        }
        return merged;
    }
}
