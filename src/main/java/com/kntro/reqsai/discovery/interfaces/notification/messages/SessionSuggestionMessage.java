package com.kntro.reqsai.discovery.interfaces.notification.messages;

import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for suggestion lifecycle events:
 * {@link SessionEventType#SUGGESTION_GENERATED}, {@link SessionEventType#SUGGESTION_ACCEPTED},
 * {@link SessionEventType#SUGGESTION_DISMISSED}.
 *
 * <p>A single record covers all three event types (discriminated by {@link #type()}),
 * which keeps the client contract stable as the lifecycle evolves.
 */
public record SessionSuggestionMessage(
        UUID sessionId,
        UUID suggestionId,
        SessionEventType type,
        SuggestionType suggestionType,
        SuggestionStatus status,
        @Nullable String draftTitle,
        @Nullable String draftRole,
        @Nullable String draftAction,
        @Nullable String draftBenefit,
        @Nullable Priority draftPriority,
        @Nullable Integer draftStoryPoints,
        @Nullable String relatedTopic,
        @Nullable UUID targetStoryId,
        @Nullable String question,
        @Nullable UUID resolvedStoryId,
        Instant occurredAt
) implements SessionRealtimeMessage {

    @Override
    public SessionEventType type() {
        return type;
    }
}
