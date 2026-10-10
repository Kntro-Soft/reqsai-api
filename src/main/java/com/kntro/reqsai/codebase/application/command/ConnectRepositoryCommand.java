package com.kntro.reqsai.codebase.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Connects a GitHub repository to a project and starts indexing it.
 *
 * @param repository     {@code owner/name} or a github.com URL
 * @param branch         the branch to read; the repository's default branch when blank
 * @param installationId the organization's GitHub App installation that shares it; when absent, the
 *                       installation on the repository's account is used if there is one, else the
 *                       repository is read anonymously (public repositories only)
 */
public record ConnectRepositoryCommand(UUID projectId, String repository, @Nullable String branch,
                                       @Nullable Long installationId) {
}
