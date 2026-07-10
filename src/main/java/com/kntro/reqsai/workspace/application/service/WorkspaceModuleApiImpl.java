package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.GlossaryTermSnapshot;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.WorkspaceSearchRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class WorkspaceModuleApiImpl implements WorkspaceModuleApi {

    private final ProjectRepository projects;
    private final GlossaryRepository glossaries;
    private final WorkspaceSearchRepository searchRepository;
    private final OrganizationRepository organizations;
    private final ProjectPermissionService projectPermissions;
    private final ProjectAccessService projectAccess;
    private final MemberRepository members;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSnapshot> findProjectSnapshot(UUID projectId) {
        return projects.findById(projectId)
                .map(project -> toSnapshot(project, glossaries.findByProjectId(projectId).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSnapshot> findRelevantContext(UUID projectId, float[] queryEmbedding, int topK) {
        return projects.findById(projectId).map(project -> {
            List<GlossaryTerm> similarTerms = searchRepository.findSimilarTerms(projectId, queryEmbedding, topK);
            List<ProjectConstraint> similarConstraints = searchRepository.findSimilarConstraints(projectId, queryEmbedding, topK);

            // Fall back to full list if nothing is embedded yet
            List<GlossaryTermSnapshot> terms = similarTerms.isEmpty()
                    ? glossaries.findByProjectId(projectId)
                            .map(g -> g.getTerms().stream().map(t -> new GlossaryTermSnapshot(t.getTerm(), t.getDefinition())).toList())
                            .orElse(List.of())
                    : similarTerms.stream().map(t -> new GlossaryTermSnapshot(t.getTerm(), t.getDefinition())).toList();

            List<String> constraints = similarConstraints.isEmpty()
                    ? project.getConstraints().stream().map(ProjectConstraint::getDescription).toList()
                    : similarConstraints.stream().map(ProjectConstraint::getDescription).toList();

            var tp = project.getTechnicalProfile();
            return new ProjectSnapshot(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    tp.programmingLanguages(),
                    tp.frameworks(),
                    tp.clientPlatforms(),
                    tp.databases(),
                    tp.architecture(),
                    tp.domain(),
                    constraints,
                    terms
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean callerHasProjectPermission(UUID projectId, UUID userId, String permission) {
        Permission required = Permission.valueOf(permission);
        UUID orgId = currentTenantOrgId();
        if (orgId == null) {
            return false;
        }
        return organizations.findById(orgId)
                .map(org -> projectPermissions.hasPermission(org, projectId, userId, required))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean callerCanAccessProject(UUID projectId, UUID userId) {
        UUID orgId = currentTenantOrgId();
        if (orgId == null) {
            return false;
        }
        return organizations.findById(orgId)
                .map(org -> projectAccess.canAccessProject(org, projectId, userId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findMemberDisplayName(UUID organizationId, UUID userId) {
        return members.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, MemberStatus.ACTIVE)
                .map(Member::getDisplayName);
    }

    /** The organization bound to the current request/callback thread, or {@code null} when none is. */
    private static UUID currentTenantOrgId() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            return null;
        }
        try {
            return UUID.fromString(tenant);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ProjectSnapshot toSnapshot(Project project, Glossary glossary) {
        var tp = project.getTechnicalProfile();

        List<String> constraints = project.getConstraints().stream()
                .map(ProjectConstraint::getDescription)
                .toList();

        List<GlossaryTermSnapshot> terms = glossary == null ? List.of() :
                glossary.getTerms().stream()
                        .map(t -> new GlossaryTermSnapshot(t.getTerm(), t.getDefinition()))
                        .toList();

        return new ProjectSnapshot(
                project.getId(),
                project.getName(),
                project.getDescription(),
                tp.programmingLanguages(),
                tp.frameworks(),
                tp.clientPlatforms(),
                tp.databases(),
                tp.architecture(),
                tp.domain(),
                constraints,
                terms
        );
    }
}
