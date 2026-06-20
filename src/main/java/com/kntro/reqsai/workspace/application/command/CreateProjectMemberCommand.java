package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record CreateProjectMemberCommand(
        UUID organizationId,
        UUID projectId,
        UUID memberId,
        UUID roleId,
        UUID requestedBy
) {}
