package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The short transactions around a client-document analysis, kept apart from the analysis itself so no
 * database connection is held while the text is extracted and the AI classifies it:
 * <ol>
 *   <li>{@link #preflight} checks the project before any work is spent on the file and reads what the
 *       project already knows (glossary terms and constraints) to flag duplicates;</li>
 *   <li>{@link #savePending} stores the analyzed document as {@code PENDING}, replacing an unapplied
 *       analysis of the same file and dropping analyses abandoned for more than {@link #PENDING_TTL}.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class PendingDocumentService {

    /** An analysis the analyst never applied nor discarded is removed after this long. */
    static final Duration PENDING_TTL = Duration.ofHours(24);

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;
    private final GlossaryRepository glossaries;

    /** Fails fast when the project is missing/archived or its document quota is used up. */
    @Transactional(readOnly = true)
    public ProjectKnowledge preflight(UUID organizationId, UUID projectId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(organizationId));
        Project project = projects.findByIdAndOrganizationIdAndStatus(projectId, organizationId, ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(projectId));

        int maxDocuments = organization.getPlanLimits().maxDocumentsPerProject();
        if (maxDocuments != -1 && documents.countByProjectIdAndStatus(projectId, DocumentStatus.ACTIVE) >= maxDocuments) {
            throw WorkspaceExceptions.projectDocumentPlanLimitExceeded(maxDocuments);
        }

        Set<String> terms = glossaries.findByProjectId(projectId)
                .map(glossary -> glossary.getTerms().stream()
                        .map(GlossaryTerm::getTerm)
                        .map(ClientDocumentSuggestions::key)
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
        Set<String> constraints = project.getConstraints().stream()
                .map(ProjectConstraint::getDescription)
                .map(ClientDocumentSuggestions::key)
                .collect(Collectors.toUnmodifiableSet());
        return new ProjectKnowledge(project.getName(), project.getTechnicalProfile().domain(), terms, constraints);
    }

    /** Stores the analyzed document, first removing older unapplied analyses it supersedes. */
    @Transactional
    public ProjectDocument savePending(ProjectDocument document) {
        Instant staleBefore = Instant.now().minus(PENDING_TTL);
        List<ProjectDocument> superseded = documents.findAllByProjectIdAndStatus(
                        document.getProjectId(), DocumentStatus.PENDING).stream()
                .filter(pending -> pending.getName().equalsIgnoreCase(document.getName())
                        || (pending.getCreatedAt() != null && pending.getCreatedAt().isBefore(staleBefore)))
                .toList();
        documents.deleteAllAndFlush(superseded);
        return documents.save(document);
    }

    /**
     * What the project already holds, keyed with {@link ClientDocumentSuggestions#key} for duplicate checks.
     *
     * @param projectName   the project name, given to the classifier
     * @param projectDomain the project's business domain, when set
     * @param glossaryKeys  keys of the terms already in the glossary
     * @param constraintKeys keys of the constraints already recorded
     */
    public record ProjectKnowledge(String projectName, @Nullable String projectDomain,
                                   Set<String> glossaryKeys, Set<String> constraintKeys) {

        public boolean hasTerm(String term) {
            return glossaryKeys.contains(ClientDocumentSuggestions.key(term));
        }

        public boolean hasConstraint(String description) {
            return constraintKeys.contains(ClientDocumentSuggestions.key(description));
        }
    }
}
