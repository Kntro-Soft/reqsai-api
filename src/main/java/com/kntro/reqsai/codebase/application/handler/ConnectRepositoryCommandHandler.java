package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.ConnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RemoteRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.service.CodeIndexLauncher;
import com.kntro.reqsai.codebase.application.service.RepositoryAccess;
import com.kntro.reqsai.codebase.application.service.RepositoryReference;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Connects a repository: checks with GitHub that it exists and that the branch does, stores it and starts
 * indexing in the background. It is read through the organization's GitHub App installation that shares it
 * (the one chosen in the picker, or found for a typed repository), which also reaches private repositories and
 * keeps the index up to date on every push; otherwise only a public repository can be read, anonymously. GitHub is asked before anything is
 * saved, so a typo or a repository not shared with ReqsAI is answered right away.
 */
@Component
public class ConnectRepositoryCommandHandler {

    private final CodeRepositoryRepository repositories;
    private final CodeHostPort host;
    private final RepositoryAccess access;
    private final CodeIndexLauncher launcher;
    private final int maxPerProject;

    public ConnectRepositoryCommandHandler(CodeRepositoryRepository repositories, CodeHostPort host,
                                           RepositoryAccess access, CodeIndexLauncher launcher,
                                           @Value("${reqsai.codebase.max-repositories-per-project:3}") int maxPerProject) {
        this.repositories = repositories;
        this.host = host;
        this.access = access;
        this.launcher = launcher;
        this.maxPerProject = maxPerProject;
    }

    public CodeRepository handle(ConnectRepositoryCommand command) {
        RepositoryReference ref = RepositoryReference.parse(command.repository());
        if (repositories.findAllByProjectId(command.projectId()).size() >= maxPerProject) {
            throw CodebaseExceptions.limitReached(maxPerProject);
        }
        if (repositories.existsByProjectIdAndFullName(command.projectId(), ref.owner(), ref.name())) {
            throw CodebaseExceptions.alreadyConnected(ref.owner() + "/" + ref.name());
        }
        Long installationId = command.installationId();
        if (installationId != null) {
            access.linked(installationId);
            if (!access.shares(installationId, ref.owner(), ref.name())) {
                throw CodebaseExceptions.notFound(ref.owner() + "/" + ref.name());
            }
        } else {
            installationId = access.installationFor(ref.owner(), ref.name()).orElse(null);
        }
        RemoteRepository remote = host.describe(ref.owner(), ref.name(), access.tokenFor(installationId));
        if (repositories.existsByProjectIdAndFullName(command.projectId(), remote.owner(), remote.name())) {
            throw CodebaseExceptions.alreadyConnected(remote.owner() + "/" + remote.name());
        }
        String branch = firstNonBlank(command.branch(), ref.branch(), remote.defaultBranch());
        host.headCommit(remote.owner(), remote.name(), branch, access.tokenFor(installationId));

        CodeRepository repository = repositories.save(CodeRepository.connect(command.projectId(), remote.owner(),
                remote.name(), branch, remote.htmlUrl(), remote.isPrivate(), installationId, Instant.now()));
        launcher.launch(repository.getId());
        return repository;
    }

    private static String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return "main";
    }
}
