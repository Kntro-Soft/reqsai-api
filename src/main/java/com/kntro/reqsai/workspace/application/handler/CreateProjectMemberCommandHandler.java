package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.CreateProjectMemberCommand;
import com.kntro.reqsai.workspace.application.event.ProjectMemberAssignedEvent;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Direct assignment of an already-ACTIVE organization member to a project. On success it publishes a
 * {@link ProjectMemberAssignedEvent} so the member is notified by email that they were added. Publishing
 * is deferred to the notification listener via Spring Modulith (AFTER_COMMIT), so the mail is sent only
 * once the assignment has committed. This event is intentionally NOT raised on the accept-a-project-
 * invitation path ({@code ProjectAssignmentMaterializer}) — those members already learned about the
 * project from the invitation email.
 */
@Component
@RequiredArgsConstructor
public class CreateProjectMemberCommandHandler {

    private final ProjectRepository projects;
    private final MemberRepository members;
    private final ProjectRoleRepository roles;
    private final ProjectMemberRepository assignments;
    private final ApplicationEventPublisher events;

    @Transactional
    public ProjectMember handle(CreateProjectMemberCommand command) {
        Project project = projects
                .findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        Member member = members.findByIdAndOrganizationIdAndStatusIn(command.memberId(), command.organizationId(),
                        List.of(MemberStatus.ACTIVE))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));

        ProjectRole role = roles.findByIdAndProjectId(command.roleId(), command.projectId())
                .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(command.roleId()));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw WorkspaceExceptions.memberNotFound(command.memberId());
        }
        if (assignments.existsByProjectIdAndMemberId(command.projectId(), command.memberId())) {
            throw WorkspaceExceptions.projectMemberAlreadyExists(command.memberId());
        }

        ProjectMember assignment = assignments.save(new ProjectMember(
                command.projectId(), command.memberId(), role.getId(), command.requestedBy(), Instant.now()));

        events.publishEvent(ProjectMemberAssignedEvent.of(
                command.organizationId(), project.getId(), project.getName(),
                member.getId(), role.getId(), role.getName(),
                member.getEmail(), member.getDisplayName()));

        return assignment;
    }
}
