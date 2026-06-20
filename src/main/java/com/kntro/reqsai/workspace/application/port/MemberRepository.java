package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findByIdAndOrganizationIdAndStatusIn(UUID id, UUID organizationId, Collection<MemberStatus> statuses);
    List<Member> findAllByOrganizationIdAndStatusIn(UUID organizationId, Collection<MemberStatus> statuses);
    boolean existsByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MemberStatus status);
    boolean existsByOrganizationIdAndEmailAndStatusIn(UUID organizationId, String email, Collection<MemberStatus> statuses);
    int countByOrganizationIdAndStatus(UUID organizationId, MemberStatus status);
    Optional<Member> findByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MemberStatus status);
}
