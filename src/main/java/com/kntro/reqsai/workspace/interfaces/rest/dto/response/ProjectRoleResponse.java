package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import com.kntro.reqsai.workspace.domain.model.Permission;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProjectRoleResponse(
        UUID id,
        UUID projectId,
        String name,
        Set<Permission> permissions,
        Instant createdAt,
        Instant updatedAt
) {}
