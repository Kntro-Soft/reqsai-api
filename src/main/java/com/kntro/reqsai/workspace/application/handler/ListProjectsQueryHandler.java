package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lists the projects of an organization (tenant-scoped), newest first. */
@Component
@RequiredArgsConstructor
public class ListProjectsQueryHandler {

    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public List<Project> handle(UUID organizationId) {
        return projects.findAllByOrganizationId(organizationId);
    }
}
