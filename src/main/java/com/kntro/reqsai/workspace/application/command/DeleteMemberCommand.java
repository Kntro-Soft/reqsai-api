package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteMemberCommand(
        UUID organizationId,
        UUID memberId,
        UUID requestedBy
) {}
