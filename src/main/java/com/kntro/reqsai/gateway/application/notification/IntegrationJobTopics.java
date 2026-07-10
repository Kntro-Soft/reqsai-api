package com.kntro.reqsai.gateway.application.notification;

import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Single source of truth for the integration-job realtime <strong>destination</strong>. Like the
 * discovery {@code ProjectTopics}, this is a <em>logical</em> topic name (no broker prefix) — the
 * shared {@link RealtimeNotifier#broadcast(String, Object)} prepends {@code /topic}, so
 * {@code jobsOf(id)} reaches subscribers on {@code /topic/projects/{id}/integration-jobs}.
 *
 * <p>A viewer on any page of the project subscribes here to render the global progress banner for
 * background Jira import / push-all jobs. Subscription auth follows the same model as the other
 * {@code /topic/projects/{id}/...} topics: the STOMP CONNECT frame is JWT-authenticated by
 * {@code StompAuthChannelInterceptor}; no per-destination gate exists, and none is added here.
 */
public final class IntegrationJobTopics {

    static final String PROJECTS_PREFIX = "projects/";
    static final String JOBS_SUFFIX = "/integration-jobs";

    private IntegrationJobTopics() {
    }

    /**
     * Logical topic carrying every sync-job progress update of one project.
     *
     * @param projectId the project aggregate id (required)
     * @return {@code "projects/{projectId}/integration-jobs"} — the notifier adds the {@code /topic} prefix
     */
    public static String jobsOf(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return PROJECTS_PREFIX + projectId + JOBS_SUFFIX;
    }
}
