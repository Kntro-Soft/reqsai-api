package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectsQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListProjectsQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "name", "status", "updatedAt", "createdAt");

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<Project> handle(ListProjectsQuery query) {
        organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        return projects.findAllByOrganizationId(
                query.organizationId(),
                pageRequestFactory.toPageable(query.criteria(), SORT));
    }
}
