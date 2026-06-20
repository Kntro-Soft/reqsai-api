package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record UpdateProjectMemberCommand(
        UUID organizationId,
        UUID projectId,
        UUID assignmentId,
        UUID roleId,
        UUID requestedBy
) {}
