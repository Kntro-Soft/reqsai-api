package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.command.ConnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RemoteRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.port.TokenCipher;
import com.kntro.reqsai.codebase.application.service.CodeIndexLauncher;
import com.kntro.reqsai.codebase.application.service.RepositoryReference;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Connects a repository: checks with GitHub that it exists and that the branch does (with the token for a
 * private one), stores it with the token encrypted, and starts indexing in the background. The host is
 * asked before anything is saved, so a typo or a rejected token is answered right away.
 */
@Component
public class ConnectRepositoryCommandHandler {

    private final CodeRepositoryRepository repositories;
    private final CodeHostPort host;
    private final TokenCipher tokenCipher;
    private final CodeIndexLauncher launcher;
    private final int maxPerProject;

    public ConnectRepositoryCommandHandler(CodeRepositoryRepository repositories, CodeHostPort host,
                                           TokenCipher tokenCipher, CodeIndexLauncher launcher,
                                           @Value("${reqsai.codebase.max-repositories-per-project:3}") int maxPerProject) {
        this.repositories = repositories;
        this.host = host;
        this.tokenCipher = tokenCipher;
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
        String token = command.accessToken() == null || command.accessToken().isBlank()
                ? null : command.accessToken().strip();
        RemoteRepository remote = host.describe(ref.owner(), ref.name(), token);
        if (repositories.existsByProjectIdAndFullName(command.projectId(), remote.owner(), remote.name())) {
            throw CodebaseExceptions.alreadyConnected(remote.owner() + "/" + remote.name());
        }
        String branch = firstNonBlank(command.branch(), ref.branch(), remote.defaultBranch());
        host.headCommit(remote.owner(), remote.name(), branch, token);

        CodeRepository repository = repositories.save(CodeRepository.connect(command.projectId(), remote.owner(),
                remote.name(), branch, remote.htmlUrl(), remote.isPrivate(),
                token == null ? null : tokenCipher.encrypt(token), Instant.now()));
        launcher.launch(repository.getId());
        return repository;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return "main";
    }
}
