/**
 * Search — global search aggregator bounded context.
 * <p>
 * Powers the frontend command palette ({@code GET /api/search?q=...}). Fans out to each bounded
 * context's {@code search} named interface, merges the top matches across types, caps the merged list,
 * and maps the result to the REST contract. Owns no tables of its own.
 * <p>
 * Layers: {@code application} (fan-out/merge service) and {@code interfaces} (REST). Depends only on the
 * OPEN {@code shared} module and the {@code search} named interfaces of workspace and discovery — it
 * never reaches into another module's repositories or entities. Authorization is enforced inside each
 * context's port; the aggregator only resolves the caller's project scope once (via
 * {@code workspace::search}) and threads it through the project- and story-scoped searches.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "workspace::search", "discovery::search"})
package com.kntro.reqsai.search;
