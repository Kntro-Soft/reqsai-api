package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.BatchInviteMembersCommand;
import com.kntro.reqsai.workspace.application.command.ChangeMemberRoleCommand;
import com.kntro.reqsai.workspace.application.command.ChangeMemberStatusCommand;
import com.kntro.reqsai.workspace.application.command.CreateMemberCommand;
import com.kntro.reqsai.workspace.application.command.DeleteMemberCommand;
import com.kntro.reqsai.workspace.application.command.LeaveOrganizationCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.BatchInviteMembersRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ChangeMemberRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ChangeMemberStatusRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateMemberRequest;

import java.util.List;
import java.util.UUID;

public final class MemberRequestMapper {

    private MemberRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CreateMemberCommand toCommand(UUID orgId, CreateMemberRequest request, UUID requestedBy) {
        return new CreateMemberCommand(orgId, request.userId(), request.email(), request.displayName(), request.role(), requestedBy);
    }

    public static DeleteMemberCommand toDeleteCommand(UUID orgId, UUID memberId, UUID requestedBy) {
        return new DeleteMemberCommand(orgId, memberId, requestedBy);
    }

    public static ChangeMemberRoleCommand toChangeRoleCommand(UUID orgId, UUID memberId, ChangeMemberRoleRequest request, UUID requestedBy) {
        return new ChangeMemberRoleCommand(orgId, memberId, request.role(), requestedBy);
    }

    public static ChangeMemberStatusCommand toChangeStatusCommand(UUID orgId, UUID memberId, ChangeMemberStatusRequest request, UUID requestedBy) {
        return new ChangeMemberStatusCommand(orgId, memberId, request.status(), requestedBy);
    }

    public static LeaveOrganizationCommand toLeaveCommand(UUID orgId, UUID requestedBy) {
        return new LeaveOrganizationCommand(orgId, requestedBy);
    }

    public static BatchInviteMembersCommand toBatchInviteCommand(UUID orgId, BatchInviteMembersRequest request, UUID requestedBy) {
        List<BatchInviteMembersCommand.Invitation> invitations = request.invitations().stream()
                .map(item -> new BatchInviteMembersCommand.Invitation(item.email(), item.displayName(), item.role()))
                .toList();
        return new BatchInviteMembersCommand(orgId, invitations, requestedBy);
    }
}
