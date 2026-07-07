package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/**
 * Partial update of an organization. {@code name}, {@code meetingLanguage} and
 * {@code audioRetentionDays} are nullable: a {@code null} field means "leave unchanged".
 */
public record UpdateOrganizationCommand(
        UUID organizationId,
        String name,
        String meetingLanguage,
        Integer audioRetentionDays,
        UUID requestedBy
) {}
