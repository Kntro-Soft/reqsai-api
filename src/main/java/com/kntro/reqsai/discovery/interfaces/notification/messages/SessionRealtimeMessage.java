package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed contract for every payload broadcast on a session WebSocket topic.
 * <p>
 * One topic carries several message kinds; the client switches on {@link #type()} to render the
 * correct UI component. The {@code sealed} hierarchy makes the full set of messages explicit and
 * lets serializers/consumers reason about it exhaustively at compile time.
 */
public sealed interface SessionRealtimeMessage permits SessionStatusChangedMessage, SessionProcessingFailedMessage, SessionStoryGeneratedMessage, SessionTranscriptSegmentMessage, SessionSuggestionMessage, SessionLifecycleMessage {

    /** Session this update belongs to (matches the subscribed topic). */
    UUID sessionId();

    /** Wire format discriminator the client switches on. */
    SessionEventType type();

    /** When the originating domain event occurred. */
    Instant occurredAt();
}
