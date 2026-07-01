package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.LeaveOrganizationCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets an ACTIVE non-owner member leave an organization: their membership is deactivated. The organization
 * OWNER cannot leave (they must transfer ownership first).
 */
@Component
@RequiredArgsConstructor
public class LeaveOrganizationCommandHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;

    @Transactional
    public void handle(LeaveOrganizationCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        if (organization.getOwnerId().equals(command.requestedBy())) {
            throw WorkspaceExceptions.ownerCannotLeave(command.organizationId());
        }

        Member member = members.findByOrganizationIdAndUserIdAndStatus(
                        command.organizationId(), command.requestedBy(), MemberStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.requestedBy()));

        member.deactivate();
        members.save(member);
    }
}
