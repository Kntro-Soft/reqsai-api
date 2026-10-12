package com.kntro.reqsai.codebase.application.port;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * ReqsAI's GitHub App: an organization installs it on its GitHub account and picks the repositories ReqsAI
 * may read. Tokens are minted per use from the App's private key and expire within the hour; none is
 * stored. Implementations translate GitHub failures into {@code CodebaseExceptions}.
 */
public interface GitHubAppPort {

    /** Whether the server has the App configured; without it only public repositories can be read. */
    boolean isConfigured();

    /** Where a GitHub account installs the App; GitHub sends {@code state} back to the setup URL. */
    String installUrl(String state);

    /**
     * The installations the GitHub user who just went through the install can access, from the OAuth
     * {@code code} GitHub adds to the setup redirect. Proves the installation in the redirect is theirs.
     */
    Set<Long> installationsOfUser(String oauthCode);

    Installation installation(long installationId);

    /** A token that reads the installation's repositories, valid for about an hour. */
    String token(long installationId);

    /** The repositories the installation shares with ReqsAI, most recently pushed first. */
    List<AppRepository> repositories(long installationId);

    /** Whether {@code signature} ({@code X-Hub-Signature-256}) signs {@code payload} with the webhook secret. */
    boolean verifySignature(byte[] payload, @Nullable String signature);

    /** An installation: the GitHub account that installed the App and what it shares. */
    record Installation(long id, String accountLogin, String accountType, @Nullable String repositorySelection,
                        @Nullable String manageUrl, @Nullable Instant suspendedAt) {
    }

    record AppRepository(String owner, String name, String defaultBranch, String htmlUrl, boolean isPrivate,
                         @Nullable String description, @Nullable Instant pushedAt) {
    }
}
