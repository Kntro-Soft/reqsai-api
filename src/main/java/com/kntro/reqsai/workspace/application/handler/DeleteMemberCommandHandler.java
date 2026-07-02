package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.DeleteMemberCommand;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeleteMemberCommandHandler {

    private final MemberRepository members;
    private final InvitationRepository invitations;

    @Transactional
    public void handle(DeleteMemberCommand command) {
        Member member = members.findByIdAndOrganizationIdAndStatusIn(command.memberId(), command.organizationId(),
                        List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(command.memberId()));

        // Removing a PENDING member also revokes its active invitation so the emailed link stops working.
        if (member.getStatus() == MemberStatus.PENDING) {
            invitations.findByMemberIdAndStatus(member.getId(), InvitationStatus.PENDING)
                    .ifPresent(invitation -> {
                        invitation.revoke();
                        invitations.save(invitation);
                    });
        }

        member.deactivate();
        members.save(member);
    }
}
