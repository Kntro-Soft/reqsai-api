package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.shared.application.search.SearchHit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port for lexical (pg_trgm) global search over the tenant {@code user_stories} table.
 * The adapter runs the native trigram query and maps rows to {@link SearchHit} value snapshots; the
 * caller decides the project scoping and passes concrete project ids, so this port never leaks
 * unauthorized rows on its own.
 */
public interface DiscoveryLexicalSearchRepository {

    /** Stories in the whole tenant whose title matches {@code term} (owner/admin scope). */
    List<SearchHit> searchAllUserStories(String term, int limit);

    /** Stories restricted to {@code projectIds} whose title matches {@code term} (member scope). */
    List<SearchHit> searchUserStoriesInProjects(Collection<UUID> projectIds, String term, int limit);
}
