package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.CreateProjectCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateProjectCommandHandler {

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;

    @Transactional
    public Project handle(CreateProjectCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        if (projects.existsByOrganizationIdAndName(command.organizationId(), command.name())) {
            throw WorkspaceExceptions.projectNameAlreadyExists(command.name());
        }

        int currentCount = projects.countActiveByOrganizationId(command.organizationId());
        int maxProjects = organization.getPlanLimits().maxProjects();
        if (maxProjects != -1 && currentCount >= maxProjects) {
            throw WorkspaceExceptions.projectPlanLimitExceeded(maxProjects);
        }

        TechnicalProfile profile = new TechnicalProfile(
                command.programmingLanguages(),
                command.frameworks(),
                command.clientPlatforms(),
                command.databases(),
                command.architecture(),
                command.domain()
        );

        Project project = new Project(
                command.organizationId(),
                command.name(),
                command.description(),
                profile,
                command.requestedBy()
        );
        return projects.save(project);
    }
}
