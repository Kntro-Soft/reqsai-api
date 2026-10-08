package com.kntro.reqsai.discovery.application.port;

import java.util.UUID;

/**
 * Bulk removal of everything Discovery holds for one project. Exists only to restore the demo project's
 * sample data (US28): discovery sessions are otherwise permanent history and are never deleted one by one.
 * Tenant-scoped (runs on the currently bound tenant schema) and joins the caller's transaction.
 */
public interface ProjectDiscoveryDataRepository {

    /**
     * Deletes the project's suggestions, assistant chat messages, user stories (their acceptance criteria
     * and client feedback go with them), transcript segments and discovery sessions. Pending changes of the current
     * persistence context are flushed first, so they are neither lost nor resurrected.
     */
    void deleteAllByProjectId(UUID projectId);
}
