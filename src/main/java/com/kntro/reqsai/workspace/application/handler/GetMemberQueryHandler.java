package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.query.GetMemberQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetMemberQueryHandler {

    private final MemberRepository members;

    @Transactional(readOnly = true)
    public Member handle(GetMemberQuery query) {
        return members.findByIdAndOrganizationIdAndStatusIn(query.memberId(), query.organizationId(),
                        List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))
                .orElseThrow(() -> WorkspaceExceptions.memberNotFound(query.memberId()));
    }
}
