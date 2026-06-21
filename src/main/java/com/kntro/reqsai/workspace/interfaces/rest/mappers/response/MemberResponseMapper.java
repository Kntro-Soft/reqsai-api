package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MemberResponse;

public final class MemberResponseMapper {

    private MemberResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static MemberResponse toResponse(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getOrganizationId(),
                member.getUserId(),
                member.getEmail(),
                member.getDisplayName(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getInvitedBy(),
                member.getInvitedAt(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
