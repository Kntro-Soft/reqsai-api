package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.RestoreDemoProjectCommand;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.service.DemoProjectSeeder;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores the demo project's sample data (US28): wipes what the users changed — profile, glossary,
 * constraints and, through Discovery, the sessions, stories, suggestions and assistant chat — and seeds the
 * original content again, in one transaction. Only an active demo project can be restored; any other
 * project fails with {@code PROJECT_NOT_DEMO}.
 */
@Component
@RequiredArgsConstructor
public class RestoreDemoProjectCommandHandler {

    private final ProjectRepository projects;
    private final DemoProjectSeeder seeder;

    @Transactional
    public Project handle(RestoreDemoProjectCommand command) {
        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));
        project.requireDemo();
        return seeder.restore(project, command.requestedBy());
    }
}
