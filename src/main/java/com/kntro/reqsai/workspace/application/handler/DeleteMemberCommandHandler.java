package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteMemberCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeleteMemberCommandHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final OrganizationAdminAccessService access;

    @Transactional
    public void handle(DeleteMemberCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));
        access.assertOwnerOrAdmin(organization, command.requestedBy(), "manage organization members");

        Member member = members.findByIdAndOrganizationIdAndStatusIn(command.memberId(), command.organizationId(),
                        List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));
        member.deactivate();
        members.save(member);
    }
}
