package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.shared.application.search.SearchHit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port for lexical (pg_trgm) global search over workspace-owned tables. The adapter runs
 * the native trigram queries and maps rows to {@link SearchHit} value snapshots; authorization scoping
 * is decided by the caller (the {@code WorkspaceSearchPort} implementation) and passed in as concrete
 * id sets, so this port never leaks unauthorized rows on its own.
 */
public interface WorkspaceLexicalSearchRepository {

    /** Active projects in the organization whose name matches {@code term} (owner/admin scope). */
    List<SearchHit> searchProjectsInOrganization(UUID organizationId, String term, int limit);

    /** Active projects restricted to {@code projectIds} whose name matches {@code term} (member scope). */
    List<SearchHit> searchProjectsInIds(UUID organizationId, Collection<UUID> projectIds, String term, int limit);

    /** Organizations within {@code organizationIds} whose name or slug matches {@code term}. */
    List<SearchHit> searchOrganizations(Collection<UUID> organizationIds, String term, int limit);

    /** Members of the organization whose display name or email matches {@code term}. */
    List<SearchHit> searchMembers(UUID organizationId, String term, int limit);
}
