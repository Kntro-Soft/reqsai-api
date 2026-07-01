package com.kntro.reqsai.discovery.search;

import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;

import java.util.List;

/**
 * Discovery-owned slice of global search. Runs a trigram lexical query over the tenant
 * {@code user_stories} table, filtered to the projects the caller may see, and returns value snapshots.
 *
 * <p>Exposed as the {@code discovery::search} named interface for the {@code search} aggregator module.
 */
public interface DiscoverySearchPort {

    /**
     * Top-{@code limit} user stories whose title matches {@code term}, restricted to {@code scope}.
     * Returns an empty list when the caller can see no projects. Ordered by trigram similarity, best first.
     */
    List<SearchHit> searchUserStories(String term, int limit, ProjectScope scope);
}
