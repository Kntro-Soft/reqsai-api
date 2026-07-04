package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Materializes a project assignment when a project-scoped invitation is accepted. The target project's
 * {@code project_roles}/{@code project_members} live in the invited organization's tenant schema, so the
 * caller ({@code AcceptInvitationCommandHandler}) runs this under the resolved tenant context. It executes
 * in its own {@link Propagation#REQUIRES_NEW REQUIRES_NEW} transaction so a fresh Hibernate session
 * acquires a connection with the correct {@code search_path}, independent of the outer accept transaction
 * (which touches the {@code public} registry under a possibly different schema).
 * <p>
 * Must be invoked through the Spring proxy (from another bean) for {@code REQUIRES_NEW} to take effect.
 * <p>
 * Graceful degradation: if the role was deleted since the invite, the assignment is skipped (the member
 * still joins the organization). A pre-existing assignment is left untouched (idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectAssignmentMaterializer {

    private final ProjectRoleRepository projectRoles;
    private final ProjectMemberRepository projectMembers;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assign(UUID projectId, UUID roleId, UUID memberId) {
        Optional<ProjectRole> role = projectRoles.findByIdAndProjectId(roleId, projectId);
        if (role.isEmpty()) {
            log.info("Project role {} no longer exists for project {} — skipping project assignment for member {}",
                    roleId, projectId, memberId);
            return;
        }
        if (projectMembers.existsByProjectIdAndMemberId(projectId, memberId)) {
            return;
        }
        projectMembers.save(new ProjectMember(projectId, memberId, role.get().getId(), memberId, Instant.now()));
        log.info("Materialized project assignment: member {} -> project {} role {}", memberId, projectId, roleId);
    }
}
