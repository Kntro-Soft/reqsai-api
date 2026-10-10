package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.application.query.ListGitHubRepositoriesQuery;
import com.kntro.reqsai.codebase.application.result.AvailableRepository;
import com.kntro.reqsai.codebase.application.service.RepositoryAccess;
import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The repositories the organization's GitHub installations share, to pick one for the project. */
@Component
@RequiredArgsConstructor
public class ListGitHubRepositoriesQueryHandler {

    private final RepositoryAccess access;
    private final GitHubAppPort app;
    private final CodeRepositoryRepository repositories;

    public List<AvailableRepository> handle(ListGitHubRepositoriesQuery query) {
        if (!app.isConfigured()) return List.of();
        Set<String> connected = repositories.findAllByProjectId(query.projectId()).stream()
                .map(CodeRepository::fullName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<AvailableRepository> available = new ArrayList<>();
        for (CodeHostInstallation installation : access.installations()) {
            if (installation.isSuspended()) continue;
            for (var repo : app.repositories(installation.getInstallationId())) {
                String fullName = (repo.owner() + "/" + repo.name()).toLowerCase(Locale.ROOT);
                available.add(new AvailableRepository(installation.getInstallationId(), repo,
                        connected.contains(fullName)));
            }
        }
        return available;
    }
}
