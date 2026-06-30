package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.ChangeMemberRoleCommand;
import com.kntro.reqsai.workspace.application.command.CreateMemberCommand;
import com.kntro.reqsai.workspace.application.command.DeleteMemberCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ChangeMemberRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateMemberRequest;

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
}
