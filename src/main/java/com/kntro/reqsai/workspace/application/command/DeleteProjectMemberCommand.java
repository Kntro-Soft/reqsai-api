package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteProjectMemberCommand(
        UUID organizationId,
        UUID projectId,
        UUID assignmentId,
        UUID requestedBy
) {}
