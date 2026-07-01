/**
 * Named interface of the Workspace module dedicated to global search — the only search-related types
 * other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.workspace.search.WorkspaceSearchPort}, which runs trigram/prefix
 * lexical queries over workspace-owned tables (projects, and the public {@code organizations} /
 * {@code members} registries) and returns {@link com.kntro.reqsai.shared.application.search.SearchHit}
 * value snapshots. Authorization is applied inside the port; no JPA entity crosses the boundary.
 * <p>
 * Consumers declare {@code allowedDependencies = "workspace::search"} in their {@code @ApplicationModule}.
 */
@org.springframework.modulith.NamedInterface("search")
package com.kntro.reqsai.workspace.search;
