package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID memberId,
        UUID roleId,
        UUID assignedBy,
        Instant assignedAt,
        Instant createdAt,
        Instant updatedAt
) {}
