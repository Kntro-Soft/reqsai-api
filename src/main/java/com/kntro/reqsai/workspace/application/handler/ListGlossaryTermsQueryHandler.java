package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListGlossaryTermsQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.SortPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListGlossaryTermsQueryHandler {

    static final SortPolicy SORT = SortPolicy.of("term", Sort.Direction.ASC, "term", "createdAt");

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final GlossaryRepository glossaries;
    private final PageRequestFactory pageRequestFactory;

    @Transactional(readOnly = true)
    public Page<GlossaryTerm> handle(ListGlossaryTermsQuery query) {
        organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(
                        query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        glossaries.findByProjectId(query.projectId())
                .orElseThrow(() -> WorkspaceExceptions.glossaryNotFound(query.projectId()));

        return glossaries.findTermsByProjectId(
                query.projectId(),
                query.search(),
                pageRequestFactory.toPageable(query.criteria(), SORT));
    }
}
