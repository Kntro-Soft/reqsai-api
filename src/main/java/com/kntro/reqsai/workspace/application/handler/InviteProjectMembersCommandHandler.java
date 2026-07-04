package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import com.kntro.reqsai.workspace.application.command.InviteProjectMembersCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.application.service.InvitationIssuer;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invites several NEW people (by email) directly to a project atomically (GitLab/Vercel pattern). Each
 * invitation creates a PENDING organization member (org role {@code MEMBER}) plus a tokenized invitation
 * that records the target project and project-role; accepting it activates the member and materializes a
 * {@code ProjectMember} assignment (see {@code AcceptInvitationCommandHandler}).
 * <p>
 * Every invitation is validated before anything is written: the target project must be ACTIVE, each
 * {@code roleId} must belong to that project, and a duplicate email (within the batch or already present
 * in the org) fails the whole request, so the transaction rolls back and no member is created.
 */
@Component
@RequiredArgsConstructor
public class InviteProjectMembersCommandHandler {

    private static final List<MemberStatus> VISIBLE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectRoleRepository roles;
    private final MemberRepository members;
    private final InvitationIssuer invitationIssuer;

    @Transactional
    public List<Member> handle(InviteProjectMembersCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        if (command.invitations() == null || command.invitations().isEmpty()) {
            throw Exceptions.invalidValue("invitations", "at least one invitation is required");
        }

        int maxMembers = organization.getPlanLimits().maxMembers();
        Set<String> seen = new HashSet<>();
        List<Member> created = new ArrayList<>(command.invitations().size());

        for (InviteProjectMembersCommand.Invitation invitation : command.invitations()) {
            String normalizedEmail = Email.of(invitation.email()).value();

            // Each role must belong to the target project (404 if it does not).
            roles.findByIdAndProjectId(invitation.roleId(), command.projectId())
                    .orElseThrow(() -> WorkspaceExceptions.projectRoleNotFound(invitation.roleId()));

            if (!seen.add(normalizedEmail)) {
                throw WorkspaceExceptions.memberAlreadyExists(normalizedEmail);
            }
            if (members.existsByOrganizationIdAndEmailAndStatusIn(command.organizationId(), normalizedEmail, VISIBLE_STATUSES)) {
                throw WorkspaceExceptions.memberAlreadyExists(normalizedEmail);
            }

            Member member = new Member(
                    command.organizationId(),
                    null,
                    normalizedEmail,
                    invitation.displayName(),
                    OrgRole.MEMBER,
                    MemberStatus.PENDING,
                    command.requestedBy(),
                    Instant.now());
            Member saved = members.save(member);
            invitationIssuer.issueFor(
                    organization, saved, command.requestedBy(), command.projectId(), invitation.roleId());
            created.add(saved);
        }

        if (maxMembers != -1) {
            int activeCount = members.countByOrganizationIdAndStatus(command.organizationId(), MemberStatus.ACTIVE);
            if (activeCount > maxMembers) {
                throw WorkspaceExceptions.memberPlanLimitExceeded(maxMembers);
            }
        }

        return created;
    }
}
