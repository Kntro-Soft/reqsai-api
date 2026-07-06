package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload broadcast on the <strong>project-level</strong> topic
 * ({@code /topic/projects/{projectId}/sessions}) for session lifecycle transitions:
 * {@link SessionEventType#SESSION_CREATED}, {@link SessionEventType#RECORDING_STARTED},
 * {@link SessionEventType#RECORDING_PAUSED}, {@link SessionEventType#RECORDING_RESUMED},
 * {@link SessionEventType#RECORDING_STOPPED}.
 *
 * <p>Unlike the per-session {@link SessionStatusChangedMessage}, this payload is self-describing —
 * {@code status}, {@code title}, {@code language}, {@code startedAt} — because a project-page
 * viewer may have never fetched the session (someone else just created/started it) and must be
 * able to render the row (including the meeting language) without an extra request.
 */
public record SessionLifecycleMessage(
        UUID sessionId,
        UUID projectId,
        SessionEventType type,
        SessionStatus status,
        String title,
        String language,
        @Nullable Instant startedAt,
        Instant occurredAt
) implements SessionRealtimeMessage {
}
