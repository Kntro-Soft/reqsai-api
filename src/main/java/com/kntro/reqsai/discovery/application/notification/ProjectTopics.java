package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Single source of truth for project-level discovery realtime <strong>destinations</strong>.
 * These are <em>logical</em> topic names (no broker prefix) — as with {@link SessionTopics}, the
 * shared {@link RealtimeNotifier#broadcast(String, Object)} prepends the configured {@code /topic}
 * prefix, so {@code sessionsOf(id)} ultimately reaches subscribers on
 * {@code /topic/projects/{id}/sessions}.
 *
 * <p>A viewer sitting on a project's discovery page subscribes here to learn about session
 * lifecycle changes (created/started/paused/resumed/stopped) <em>before</em> it knows any session
 * id — per-session topics only work once the client knows which session to watch.
 */
public final class ProjectTopics {

    static final String PROJECTS_PREFIX = "projects/";
    static final String SESSIONS_SUFFIX = "/sessions";

    private ProjectTopics() {
    }

    /**
     * Logical topic carrying every session lifecycle update of one project.
     *
     * @param projectId the project aggregate id (required)
     * @return {@code "projects/{projectId}/sessions"} — the notifier adds the {@code /topic} prefix
     */
    public static String sessionsOf(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return PROJECTS_PREFIX + projectId + SESSIONS_SUFFIX;
    }
}
