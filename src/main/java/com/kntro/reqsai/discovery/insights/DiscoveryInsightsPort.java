package com.kntro.reqsai.discovery.insights;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregates of what Discovery holds, for dashboards. Every figure is
 * computed in SQL over the given projects of the currently bound tenant;
 * callers decide which projects the user may see and pass exactly those ids. An
 * empty collection yields empty (zero) insights without touching the database.
 * <p>
 * Exposed as the {@code discovery::insights} named interface.
 */
public interface DiscoveryInsightsPort {

    /**
     * Backlog figures of the projects; weekly creation counts start at
     * {@code since}.
     */
    BacklogInsights backlog(Collection<UUID> projectIds, Instant since, int recentLimit);

    /**
     * Session and suggestion figures of the projects; weekly creation counts start
     * at {@code since}.
     */
    SessionInsights sessions(Collection<UUID> projectIds, Instant since, int recentLimit);

    /**
     * Stories, sessions and last activity of each project that has any; projects
     * with none are absent.
     */
    List<ProjectActivity> activityByProject(Collection<UUID> projectIds);
}
