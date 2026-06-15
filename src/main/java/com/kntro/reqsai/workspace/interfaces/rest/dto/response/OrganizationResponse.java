package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String status,
        UUID ownerId,
        String meetingLanguage,
        int audioRetentionDays,
        Instant createdAt,
        Instant updatedAt
) {
}
