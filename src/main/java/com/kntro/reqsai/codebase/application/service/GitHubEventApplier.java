package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Applies one GitHub App event to the repositories of the tenant bound to the thread, in one short
 * transaction. The caller ({@link GitHubWebhookService}) runs it once per organization the installation serves.
 */
@Component
@RequiredArgsConstructor
public class GitHubEventApplier {

    private final CodeRepositoryRepository repositories;
    private final CodeIndexLauncher launcher;

    /** A push to {@code branch}: the repositories tracking it reindex (or queue the commit while a run is on). */
    @Transactional
    public int push(long installationId, String owner, String name, String branch, String commit) {
        Instant now = Instant.now();
        int started = 0;
        for (CodeRepository repo : repositories.findAllByInstallationId(installationId)) {
            if (!repo.tracks(owner, name, branch)) continue;
            boolean run = repo.acceptPush(commit, now);
            repositories.save(repo);
            if (run) {
                launcher.launch(repo.getId());
                started++;
            }
        }
        return started;
    }

    /**
     * GitHub stopped sharing repositories with ReqsAI: those named in {@code fullNames} (every repository of
     * the installation when null) stop updating and say why.
     */
    @Transactional
    public int revoke(long installationId, @Nullable Set<String> fullNames, String reason) {
        Instant now = Instant.now();
        int revoked = 0;
        for (CodeRepository repo : repositories.findAllByInstallationId(installationId)) {
            if (fullNames != null && !fullNames.contains(repo.fullName().toLowerCase(Locale.ROOT))) continue;
            repo.revokeAccess(reason, now);
            repositories.save(repo);
            revoked++;
        }
        return revoked;
    }
}
