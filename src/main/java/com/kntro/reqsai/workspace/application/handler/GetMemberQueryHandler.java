package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.GetMemberQuery;
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
public class GetMemberQueryHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final OrganizationAdminAccessService access;

    @Transactional(readOnly = true)
    public Member handle(GetMemberQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));
        access.assertOwnerOrAdmin(organization, query.requestedBy(), "view organization members");

        return members.findByIdAndOrganizationIdAndStatusIn(query.memberId(), query.organizationId(),
                        List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(query.memberId()));
    }
}
