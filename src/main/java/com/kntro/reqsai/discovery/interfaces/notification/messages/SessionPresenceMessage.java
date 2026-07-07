package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WebSocket payload for {@link SessionEventType#PRESENCE_STATE}: the full roster of users currently
 * viewing a live discovery session.
 * <p>
 * The message is a <em>snapshot</em> (the complete list, not a delta) so the client renders it
 * idempotently and never drifts if it misses an intermediate join/leave. {@code count} is the number
 * of distinct participants — the same user viewing from two tabs appears once.
 */
public record SessionPresenceMessage(
        UUID sessionId,
        List<SessionParticipant> participants,
        int count,
        Instant occurredAt
) implements SessionRealtimeMessage {

    /** Builds a presence snapshot, deriving {@code count} from the participant list. */
    public static SessionPresenceMessage of(UUID sessionId, List<SessionParticipant> participants, Instant occurredAt) {
        return new SessionPresenceMessage(sessionId, List.copyOf(participants), participants.size(), occurredAt);
    }

    @Override
    public SessionEventType type() {
        return SessionEventType.PRESENCE_STATE;
    }
}
