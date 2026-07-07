package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        String email,
        String displayName,
        String role,
        String status,
        UUID invitedBy,
        Instant invitedAt,
        Instant createdAt,
        Instant updatedAt
) {}
