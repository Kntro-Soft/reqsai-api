package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectsQuery;
import com.kntro.reqsai.workspace.application.service.ProjectAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListProjectsQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("createdAt", Sort.Direction.DESC, "name", "status", "updatedAt", "createdAt");

    private final ProjectRepository projects;
    private final OrganizationRepository organizations;
    private final ProjectAccessService projectAccess;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<Project> handle(ListProjectsQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        // Owners/admins implicitly see every project; regular members see only their assigned ones.
        Optional<Set<UUID>> accessibleProjectIds = projectAccess.accessibleProjectIds(organization, query.requestedBy());
        Pageable pageable = pageRequestFactory.toPageable(query.criteria(), SORT);

        if (accessibleProjectIds.isEmpty()) {
            return projects.findAllByOrganizationIdAndStatus(query.organizationId(), ProjectStatus.ACTIVE, pageable);
        }
        Set<UUID> ids = accessibleProjectIds.get();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        return projects.findAllByOrganizationIdAndStatusAndIdIn(query.organizationId(), ProjectStatus.ACTIVE, ids, pageable);
    }
}
