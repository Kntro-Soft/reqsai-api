package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.handler.ConnectRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import com.kntro.reqsai.workspace.api.DetectedStack;
import com.kntro.reqsai.workspace.api.ProjectSetupParticipant;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.SetupStepView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Codebase's part in a project's setup: it refuses repositories GitHub cannot confirm before the project exists,
 * reads the repositories step from the indexing state that already persists, and offers the stack found in the
 * repositories that finished.
 */
@Component
class CodeSetupParticipant implements ProjectSetupParticipant {

    private final ConnectRepositoryCommandHandler connect;
    private final CodeRepositoryRepository repositories;
    private final int maxPerProject;

    CodeSetupParticipant(ConnectRepositoryCommandHandler connect, CodeRepositoryRepository repositories,
                         @Value("${reqsai.codebase.max-repositories-per-project:3}") int maxPerProject) {
        this.connect = connect;
        this.repositories = repositories;
        this.maxPerProject = maxPerProject;
    }

    @Override
    public void validate(ProjectSources sources, UUID requestedBy) {
        List<ProjectSources.GitHub> requested = sources.github();
        if (requested.isEmpty()) return;
        if (requested.size() > maxPerProject) throw CodebaseExceptions.limitReached(maxPerProject);
        Set<String> seen = new HashSet<>();
        for (ProjectSources.GitHub repo : requested) {
            var resolved = connect.resolve(repo.repository(), repo.branch(), repo.installationId());
            String fullName = resolved.remote().owner() + "/" + resolved.remote().name();
            if (!seen.add(fullName.toLowerCase(Locale.ROOT))) throw CodebaseExceptions.alreadyConnected(fullName);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SetupStepView> steps(UUID projectId) {
        return stepOf(repositories.findAllByProjectId(projectId)).stream().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetectedStack> detectedStack(UUID projectId) {
        return Optional.of(stackOf(repositories.findAllByProjectId(projectId)));
    }

    /** Running while any repository is indexing, failed when one failed, done when all are ready. */
    static Optional<SetupStepView> stepOf(List<CodeRepository> repos) {
        if (repos.isEmpty()) return Optional.empty();
        int done = repos.stream().mapToInt(CodeRepository::getModulesDone).sum();
        int total = repos.stream().mapToInt(CodeRepository::getModuleCount).sum();
        if (repos.stream().anyMatch(repo -> repo.getStatus().isRunning())) {
            return Optional.of(new SetupStepView(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING, done, total, null));
        }
        Optional<CodeRepository> failed = repos.stream()
                .filter(repo -> repo.getStatus() == CodeRepositoryStatus.FAILED).findFirst();
        if (failed.isPresent()) {
            String reason = failed.get().getError();
            return Optional.of(new SetupStepView(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.FAILED, done, total,
                    failed.get().fullName() + (reason == null ? "" : ": " + reason)));
        }
        return Optional.of(new SetupStepView(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.DONE, total, total, null));
    }

    /** The technology of the ready repositories, merged without repeats (accents and case ignored). */
    static DetectedStack stackOf(List<CodeRepository> repos) {
        List<CodeProfile> profiles = repos.stream()
                .filter(repo -> repo.getStatus() == CodeRepositoryStatus.READY)
                .map(CodeRepository::getProfile)
                .toList();
        return new DetectedStack(
                union(profiles, CodeProfile::languages),
                union(profiles, CodeProfile::frameworks),
                union(profiles, CodeProfile::databases),
                union(profiles, CodeProfile::platforms));
    }

    private static List<String> union(List<CodeProfile> profiles, Function<CodeProfile, List<String>> field) {
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (CodeProfile profile : profiles) {
            for (String value : field.apply(profile)) {
                if (value == null || value.isBlank()) continue;
                String key = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                        .strip().toLowerCase(Locale.ROOT);
                if (seen.add(key)) out.add(value.strip());
            }
        }
        return out;
    }
}
