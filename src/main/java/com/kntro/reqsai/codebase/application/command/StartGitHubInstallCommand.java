package com.kntro.reqsai.codebase.application.command;

import java.util.UUID;

/** Starts installing the ReqsAI GitHub App for an organization. */
public record StartGitHubInstallCommand(UUID organizationId, UUID userId) {
}
