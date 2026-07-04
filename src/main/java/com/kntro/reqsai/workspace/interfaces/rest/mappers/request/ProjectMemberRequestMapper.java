package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateProjectMemberCommand;
import com.kntro.reqsai.workspace.application.command.DeleteProjectMemberCommand;
import com.kntro.reqsai.workspace.application.command.InviteProjectMembersCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectMemberCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.InviteProjectMembersRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectMemberRequest;

import java.util.List;
import java.util.UUID;

public final class ProjectMemberRequestMapper {

    private ProjectMemberRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CreateProjectMemberCommand toCommand(UUID orgId, UUID projectId, CreateProjectMemberRequest request, UUID requestedBy) {
        return new CreateProjectMemberCommand(orgId, projectId, request.memberId(), request.roleId(), requestedBy);
    }

    public static UpdateProjectMemberCommand toCommand(UUID orgId, UUID projectId, UUID assignmentId, UpdateProjectMemberRequest request, UUID requestedBy) {
        return new UpdateProjectMemberCommand(orgId, projectId, assignmentId, request.roleId(), requestedBy);
    }

    public static DeleteProjectMemberCommand toDeleteCommand(UUID orgId, UUID projectId, UUID assignmentId, UUID requestedBy) {
        return new DeleteProjectMemberCommand(orgId, projectId, assignmentId, requestedBy);
    }

    public static InviteProjectMembersCommand toInviteCommand(UUID orgId, UUID projectId, InviteProjectMembersRequest request, UUID requestedBy) {
        List<InviteProjectMembersCommand.Invitation> invitations = request.invitations().stream()
                .map(item -> new InviteProjectMembersCommand.Invitation(item.email(), item.displayName(), item.roleId()))
                .toList();
        return new InviteProjectMembersCommand(orgId, projectId, invitations, requestedBy);
    }
}
