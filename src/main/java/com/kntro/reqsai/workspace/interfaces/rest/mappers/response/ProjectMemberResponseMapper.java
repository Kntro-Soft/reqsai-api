package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectMemberResponse;

public final class ProjectMemberResponseMapper {

    private ProjectMemberResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ProjectMemberResponse toResponse(ProjectMember assignment) {
        return new ProjectMemberResponse(
                assignment.getId(),
                assignment.getProjectId(),
                assignment.getMemberId(),
                assignment.getRoleId(),
                assignment.getAssignedBy(),
                assignment.getAssignedAt(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
