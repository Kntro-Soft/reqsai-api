package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.ApplyClientDocumentCommand;
import com.kntro.reqsai.workspace.application.command.ApplyClientDocumentCommand.GlossaryTermInput;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.result.ClientDocumentApplyResult;
import com.kntro.reqsai.workspace.application.service.ClientDocumentSuggestions;
import com.kntro.reqsai.workspace.application.service.ProjectPermissionService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the analyst's review of an uploaded client document (US22) in one transaction: the selected
 * glossary terms and constraints that the project does not have yet are added (the others are counted
 * as skipped), and the document becomes {@code ACTIVE} with the reviewed name, type and context summary,
 * so the AI reads it as project context from then on.
 * <p>
 * Adding terms or constraints needs the same permissions as their own pages
 * ({@code GLOSSARY_TERM_WRITE}, {@code CONSTRAINT_WRITE}); plan limits for documents and glossary terms
 * are enforced.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplyClientDocumentCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;
    private final GlossaryRepository glossaries;
    private final ProjectPermissionService permissions;

    @Transactional
    public ClientDocumentApplyResult handle(ApplyClientDocumentCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        Project project = projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));
        ProjectDocument document = documents.findByIdAndProjectIdAndStatusIn(command.documentId(), command.projectId(),
                        Set.of(DocumentStatus.PENDING, DocumentStatus.ACTIVE))
                .orElseThrow(() -> WorkspaceExceptions.projectDocumentNotFound(command.documentId()));
        if (!document.isPending()) {
            throw WorkspaceExceptions.projectDocumentNotPending(document.getId());
        }

        String name = ProjectDocument.normalizeName(
                command.name() == null || command.name().isBlank() ? document.getName() : command.name());
        if (documents.existsByProjectIdAndNameAndStatus(command.projectId(), name, DocumentStatus.ACTIVE)) {
            throw WorkspaceExceptions.projectDocumentAlreadyExists(name);
        }
        int maxDocuments = organization.getPlanLimits().maxDocumentsPerProject();
        if (maxDocuments != -1
                && documents.countByProjectIdAndStatus(command.projectId(), DocumentStatus.ACTIVE) >= maxDocuments) {
            throw WorkspaceExceptions.projectDocumentPlanLimitExceeded(maxDocuments);
        }

        List<GlossaryTermInput> terms = distinctTerms(command.glossaryTerms());
        List<String> constraints = distinctConstraints(command.constraints());
        if (!terms.isEmpty()) {
            permissions.assertHasProjectPermission(organization, command.projectId(), command.requestedBy(),
                    Permission.GLOSSARY_TERM_WRITE, "add glossary terms from a client document");
        }
        if (!constraints.isEmpty()) {
            permissions.assertHasProjectPermission(organization, command.projectId(), command.requestedBy(),
                    Permission.CONSTRAINT_WRITE, "add constraints from a client document");
        }

        int termsAdded = addTerms(organization, command, terms);
        int constraintsAdded = addConstraints(project, constraints);

        document.applyReview(name, command.documentType(), command.summary());
        ProjectDocument saved = documents.save(document);
        log.info("Client document {} applied to project {}: {} term(s) added, {} constraint(s) added",
                saved.getId(), command.projectId(), termsAdded, constraintsAdded);
        return new ClientDocumentApplyResult(saved,
                termsAdded, terms.size() - termsAdded,
                constraintsAdded, constraints.size() - constraintsAdded);
    }

    private int addTerms(Organization organization, ApplyClientDocumentCommand command, List<GlossaryTermInput> terms) {
        if (terms.isEmpty()) {
            return 0;
        }
        Glossary glossary = glossaries.findByProjectId(command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.glossaryNotFound(command.projectId()));
        List<GlossaryTermInput> missing = terms.stream().filter(t -> !glossary.hasTerm(t.term())).toList();
        int maxTerms = organization.getPlanLimits().maxGlossaryTermsPerProject();
        if (maxTerms != -1 && glossary.getTerms().size() + missing.size() > maxTerms) {
            throw WorkspaceExceptions.glossaryTermPlanLimitExceeded(maxTerms);
        }
        missing.forEach(t -> glossary.addTerm(t.term(), t.definition(), command.requestedBy()));
        if (!missing.isEmpty()) {
            glossaries.save(glossary);
        }
        return missing.size();
    }

    private int addConstraints(Project project, List<String> constraints) {
        List<String> missing = constraints.stream().filter(c -> !project.hasConstraint(c)).toList();
        missing.forEach(project::addConstraint);
        if (!missing.isEmpty()) {
            projects.save(project);
        }
        return missing.size();
    }

    /** Selected terms without blanks or case-insensitive repeats (first one wins). */
    private static List<GlossaryTermInput> distinctTerms(List<GlossaryTermInput> terms) {
        Map<String, GlossaryTermInput> distinct = new LinkedHashMap<>();
        terms.stream()
                .filter(t -> t != null && t.term() != null && !t.term().isBlank())
                .forEach(t -> distinct.putIfAbsent(ClientDocumentSuggestions.key(t.term()),
                        new GlossaryTermInput(t.term().strip(), t.definition() == null ? "" : t.definition().strip())));
        return List.copyOf(distinct.values());
    }

    /** Selected constraints without blanks or case-insensitive repeats (first one wins). */
    private static List<String> distinctConstraints(List<String> constraints) {
        Map<String, String> distinct = new LinkedHashMap<>();
        constraints.stream()
                .filter(c -> c != null && !c.isBlank())
                .forEach(c -> distinct.putIfAbsent(ClientDocumentSuggestions.key(c), c.strip()));
        return List.copyOf(distinct.values());
    }
}
