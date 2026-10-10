package com.kntro.reqsai.codebase.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Completes an install with what GitHub put in the setup redirect.
 *
 * @param installationId absent when a GitHub organization member only requested the install
 * @param setupAction    {@code install}, {@code update} or {@code request}
 * @param state          the signed state issued when the install started (absent on an update from GitHub)
 * @param code           the OAuth code proving which GitHub user went through the install
 */
public record CompleteGitHubInstallCommand(UUID organizationId, UUID userId, @Nullable Long installationId,
                                           @Nullable String setupAction, @Nullable String state,
                                           @Nullable String code) {
}
