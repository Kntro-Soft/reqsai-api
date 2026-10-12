package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.command.ConnectRepositoryCommand;
import com.kntro.reqsai.codebase.application.command.ReindexRepositoryCommand;
import com.kntro.reqsai.codebase.application.handler.ConnectRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.handler.ReindexRepositoryCommandHandler;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseError;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.api.ProjectSetupReporter;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Connects the repositories a new project was created with, one by one, with the same handler the REST endpoint uses
 * and on behalf of the creator (limit and GitHub access checks included), and starts their indexing. A repository
 * that cannot be connected is recorded and does not stop the others, and nothing it does can undo the project. A
 * retry connects what is missing and indexes again what failed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectCodeSetup {

    static final String UNEXPECTED = "SETUP_STEP_FAILED";

    private final ConnectRepositoryCommandHandler connect;
    private final ReindexRepositoryCommandHandler reindex;
    private final CodeRepositoryRepository repositories;
    private final WorkspaceModuleApi workspace;
    private final ProjectSetupReporter setup;

    /**
     * Connects what the event asks for, in the tenant of the event. The listener's own transaction was opened
     * before the tenant was restored, so this suspends it: every handler then opens its own, in the tenant schema.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run(ProjectSetupRequestedIntegrationEvent event) {
        List<ProjectSources.GitHub> requested = event.sources().github();
        if (requested.isEmpty() || !event.runs(SetupStepKeys.GITHUB_REPOS)) return;
        UUID projectId = event.projectId();

        if (!workspace.callerHasProjectPermission(projectId, event.requestedBy(), "INTEGRATION_WRITE")) {
            setup.report(projectId, SetupStepKeys.GITHUB_REPOS, SetupStepStatus.FAILED, "INSUFFICIENT_PERMISSIONS");
            return;
        }
        List<String> failures = new ArrayList<>();
        int started = 0;
        for (ProjectSources.GitHub repo : requested) {
            try {
                connectOrRetry(projectId, repo);
                started++;
            } catch (RuntimeException e) {
                log.warn("Repository {} of project {} could not be set up: {}", repo.repository(), projectId,
                        e.getMessage());
                failures.add(repo.repository() + ": " + failureCode(e));
            }
        }
        String detail = failures.isEmpty() ? null : String.join("; ", failures);
        setup.report(projectId, SetupStepKeys.GITHUB_REPOS,
                started == 0 ? SetupStepStatus.FAILED : SetupStepStatus.RUNNING, detail);
    }

    private void connectOrRetry(UUID projectId, ProjectSources.GitHub repo) {
        try {
            connect.handle(new ConnectRepositoryCommand(projectId, repo.repository(), repo.branch(),
                    repo.installationId()));
        } catch (DomainException e) {
            if (e.error() != CodebaseError.CODE_REPOSITORY_ALREADY_CONNECTED) throw e;
            reindexIfFailed(projectId, repo.repository());
        }
    }

    private void reindexIfFailed(UUID projectId, String repository) {
        String wanted = RepositoryReference.parse(repository).owner().toLowerCase(Locale.ROOT) + "/"
                + RepositoryReference.parse(repository).name().toLowerCase(Locale.ROOT);
        for (CodeRepository existing : repositories.findAllByProjectId(projectId)) {
            if (existing.fullName().toLowerCase(Locale.ROOT).equals(wanted)
                    && existing.getStatus() == CodeRepositoryStatus.FAILED) {
                reindex.handle(new ReindexRepositoryCommand(projectId, existing.getId()));
            }
        }
    }

    static String failureCode(RuntimeException e) {
        return e instanceof DomainException domain ? domain.error().code() : UNEXPECTED;
    }
}
