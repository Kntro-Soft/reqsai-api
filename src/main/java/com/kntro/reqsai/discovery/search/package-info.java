/**
 * Named interface of the Discovery module dedicated to global search — the only search-related types
 * other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.discovery.search.DiscoverySearchPort}, which runs trigram lexical
 * queries over the tenant {@code user_stories} table and returns
 * {@link com.kntro.reqsai.shared.application.search.SearchHit} value snapshots, filtered to the
 * {@link com.kntro.reqsai.shared.application.search.ProjectScope} the caller may see. No JPA entity
 * crosses the boundary.
 * <p>
 * Consumers declare {@code allowedDependencies = "discovery::search"} in their {@code @ApplicationModule}.
 */
@org.springframework.modulith.NamedInterface("search")
package com.kntro.reqsai.discovery.search;
