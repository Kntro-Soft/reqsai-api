package com.kntro.reqsai.discovery.application.notification;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Single source of truth for discovery session realtime <strong>destinations</strong>.
 * These are <em>logical</em> topic names (no broker prefix): the shared
 * {@link RealtimeNotifier#broadcast(String, Object)}
 * prepends the configured {@code /topic} prefix, so {@code of(id)} ultimately reaches subscribers
 * on {@code /topic/sessions/{id}}. The client opens <em>one</em> subscription per session page and
 * discriminates on the message {@code type} field — never one topic per event kind.
 */
public final class SessionTopics {

    static final String SESSIONS_PREFIX = "sessions/";

    private SessionTopics() {
    }

    /**
     * The logical topic prefix for per-session destinations (no broker prefix). Presence tracking
     * matches subscribe destinations against {@code /topic/} + this value to recognize which session
     * a client is viewing.
     */
    public static String sessionsPrefix() {
        return SESSIONS_PREFIX;
    }

    /**
     * Logical topic carrying every realtime update for one discovery session.
     *
     * @param sessionId the session aggregate id (required)
     * @return {@code "sessions/{sessionId}"} — the notifier adds the {@code /topic} prefix
     */
    public static String of(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return SESSIONS_PREFIX + sessionId;
    }
}
