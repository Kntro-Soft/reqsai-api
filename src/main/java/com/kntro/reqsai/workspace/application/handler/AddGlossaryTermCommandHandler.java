package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AddGlossaryTermCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final GlossaryRepository glossaries;

    @Transactional
    public GlossaryTerm handle(AddGlossaryTermCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        Glossary glossary = glossaries.findByProjectId(command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.glossaryNotFound(command.projectId()));

        int maxTerms = organization.getPlanLimits().maxGlossaryTermsPerProject();
        if (maxTerms != -1 && glossary.getTerms().size() >= maxTerms) {
            throw WorkspaceExceptions.glossaryTermPlanLimitExceeded(maxTerms);
        }

        GlossaryTerm term = glossary.addTerm(command.term(), command.definition(), command.requestedBy());
        glossaries.save(glossary);
        return term;
    }
}
