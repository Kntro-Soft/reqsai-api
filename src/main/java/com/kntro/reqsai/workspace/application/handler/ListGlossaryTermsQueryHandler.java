package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListGlossaryTermsQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListGlossaryTermsQueryHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final GlossaryRepository glossaries;

    @Transactional(readOnly = true)
    public List<GlossaryTerm> handle(ListGlossaryTermsQuery query) {
        organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(
                        query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        Glossary glossary = glossaries.findByProjectId(query.projectId())
                .orElseThrow(() -> WorkspaceExceptions.glossaryNotFound(query.projectId()));

        return glossary.getTerms();
    }
}
