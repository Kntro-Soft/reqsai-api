package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.port.SessionStatsRepository.SessionStats;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/** Maps the {@link DiscoverySession} aggregate to its response DTO. */
public final class DiscoverySessionResponseMapper {

    private DiscoverySessionResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Response without aggregate stats — used by lifecycle transitions (start/pause/resume/stop/upload/
     * process). {@code durationSeconds} is still derived from the timestamps; the count fields are null.
     */
    public static DiscoverySessionResponse toResponse(DiscoverySession session) {
        return toResponse(session, null);
    }

    /**
     * Response including the history-table stats — used by the get/list session endpoints. Pass
     * {@code null} for {@code stats} to omit the counts (they render as {@code null}).
     */
    public static DiscoverySessionResponse toResponse(DiscoverySession session, @Nullable SessionStats stats) {
        return new DiscoverySessionResponse(
                session.getId(),
                session.getProjectId(),
                session.getTitle(),
                session.getLanguage().value(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getAudioDurationMs(),
                session.getProcessingError(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                durationSeconds(session),
                stats != null ? stats.storiesGenerated() : null,
                stats != null ? stats.storiesAccepted() : null,
                stats != null ? stats.suggestionsPending() : null,
                stats != null ? stats.questionsAsked() : null);
    }

    /** Recording length in seconds, derivable only once a session has both started and ended. */
    private static @Nullable Long durationSeconds(DiscoverySession session) {
        if (session.getStartedAt() == null || session.getEndedAt() == null) {
            return null;
        }
        long seconds = Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
        return Math.max(seconds, 0);
    }
}
