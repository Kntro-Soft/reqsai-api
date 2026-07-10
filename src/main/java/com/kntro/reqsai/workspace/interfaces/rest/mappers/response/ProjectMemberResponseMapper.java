package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectMemberResponse;

public final class ProjectMemberResponseMapper {

    private ProjectMemberResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ProjectMemberResponse toResponse(ProjectMember assignment) {
        return toResponse(assignment, null);
    }

    /**
     * Maps an assignment, embedding the resolved role name. The list endpoint supplies it so a
     * {@code MEMBER_READ} caller sees roles without {@code ROLE_READ}; single-assignment responses
     * pass {@code null} and the client falls back to its own role lookup.
     */
    public static ProjectMemberResponse toResponse(ProjectMember assignment, String roleName) {
        return new ProjectMemberResponse(
                assignment.getId(),
                assignment.getProjectId(),
                assignment.getMemberId(),
                assignment.getRoleId(),
                roleName,
                assignment.getAssignedBy(),
                assignment.getAssignedAt(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
