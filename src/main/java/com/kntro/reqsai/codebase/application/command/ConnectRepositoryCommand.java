package com.kntro.reqsai.codebase.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Connects a GitHub repository to a project and starts indexing it.
 *
 * @param repository  {@code owner/name} or a github.com URL
 * @param branch      the branch to read; the repository's default branch when blank
 * @param accessToken a read-only token, needed only for a private repository
 */
public record ConnectRepositoryCommand(UUID projectId, String repository, @Nullable String branch,
                                       @Nullable String accessToken) {
}
