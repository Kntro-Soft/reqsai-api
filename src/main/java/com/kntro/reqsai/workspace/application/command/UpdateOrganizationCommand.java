package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record UpdateOrganizationCommand(
        UUID organizationId,
        String name,
        String meetingLanguage,
        int audioRetentionDays,
        UUID requestedBy
) {}
