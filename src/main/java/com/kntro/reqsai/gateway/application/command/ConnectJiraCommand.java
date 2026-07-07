package com.kntro.reqsai.gateway.application.command;

import java.util.UUID;

/** Connect a Jira integration at the organization level (verifies the credential, then persists it). */
public record ConnectJiraCommand(
        UUID organizationId,
        String siteUrl,
        String email,
        String apiToken,
        UUID requestedBy
) {}
