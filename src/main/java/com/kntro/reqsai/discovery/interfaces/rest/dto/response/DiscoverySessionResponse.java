package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Flat response view of a discovery session. */
public record DiscoverySessionResponse(
        UUID id,
        UUID projectId,
        String title,
        String language,
        String status,
        Instant startedAt,
        Instant endedAt,
        long audioDurationMs,
        String processingError,
        Instant createdAt,
        Instant updatedAt
) {
}
