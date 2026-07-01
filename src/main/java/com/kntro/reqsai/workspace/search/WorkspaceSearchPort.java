package com.kntro.reqsai.workspace.search;

import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;

import java.util.List;
import java.util.UUID;

/**
 * Workspace-owned slice of global search. Runs trigram lexical queries over the projects table and the
 * public {@code organizations} / {@code members} registries, returning value snapshots. Every method
 * applies the caller's authorization so no cross-tenant or unauthorized row escapes.
 *
 * <p>Exposed as the {@code workspace::search} named interface for the {@code search} aggregator module.
 */
public interface WorkspaceSearchPort {

    /**
     * Resolves which projects the caller may see in {@code organizationId}. Org owners/admins get an
     * {@link ProjectScope#unrestricted()} scope; regular members get only their explicitly assigned
     * projects. Throws when the caller is neither owner/admin nor an active member.
     */
    ProjectScope resolveProjectScope(UUID organizationId, UUID callerId);

    /**
     * Top-{@code limit} active projects in {@code organizationId} whose name matches {@code term},
     * filtered to the caller's {@code scope}. Ordered by trigram similarity, best first.
     */
    List<SearchHit> searchProjects(String term, int limit, UUID organizationId, ProjectScope scope);

    /**
     * Top-{@code limit} organizations the caller belongs to (owns or is an active member of) whose name
     * or slug matches {@code term}. Ordered by trigram similarity, best first.
     */
    List<SearchHit> searchOrganizations(String term, int limit, UUID callerId);

    /**
     * Top-{@code limit} members of {@code organizationId} whose display name or email matches
     * {@code term}. The caller must be an active member (or owner/admin) of the organization; otherwise
     * an empty list is returned. Ordered by trigram similarity, best first.
     */
    List<SearchHit> searchMembers(String term, int limit, UUID organizationId, UUID callerId);

    /**
     * Top-{@code limit} glossary terms whose term matches {@code term}, filtered to the caller's
     * {@code scope} (the same accessible-project scope used for projects and user stories). Ordered by
     * trigram similarity, best first.
     */
    List<SearchHit> searchGlossaryTerms(String term, int limit, ProjectScope scope);

    /**
     * Top-{@code limit} project documents whose name matches {@code term}, filtered to the caller's
     * {@code scope} (the same accessible-project scope used for projects and user stories). Ordered by
     * trigram similarity, best first.
     */
    List<SearchHit> searchDocuments(String term, int limit, ProjectScope scope);
}
