package com.kntro.reqsai.codebase.application.command;

import java.util.UUID;

/** Unlinks a GitHub App installation from the organization (the App stays installed on GitHub). */
public record DisconnectGitHubInstallationCommand(UUID organizationId, long installationId) {
}
