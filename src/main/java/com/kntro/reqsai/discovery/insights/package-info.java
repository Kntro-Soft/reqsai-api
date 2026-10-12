/**
 * Named interface of the Discovery module for read-only dashboard aggregates —
 * the only insight types other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort},
 * which answers with counts computed in SQL (stories, sessions and suggestions
 * grouped by status, priority and week) over a set of project ids the caller
 * already resolved. No entity and no domain enum crosses this boundary:
 * statuses are plain strings in the enums' declaration order.
 * <p>
 * Consumers declare {@code allowedDependencies = "discovery::insights"} in
 * their {@code @ApplicationModule}.
 */
@org.springframework.modulith.NamedInterface("insights")
package com.kntro.reqsai.discovery.insights;
