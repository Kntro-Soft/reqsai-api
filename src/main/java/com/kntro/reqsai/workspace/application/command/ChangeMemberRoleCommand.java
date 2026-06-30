package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.OrgRole;

import java.util.UUID;

public record ChangeMemberRoleCommand(
        UUID organizationId,
        UUID memberId,
        OrgRole role,
        UUID requestedBy
) {}
