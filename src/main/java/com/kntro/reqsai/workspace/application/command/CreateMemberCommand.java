package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.OrgRole;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record CreateMemberCommand(
        UUID organizationId,
        @Nullable UUID userId,
        String email,
        String displayName,
        OrgRole role,
        UUID requestedBy
) {}
