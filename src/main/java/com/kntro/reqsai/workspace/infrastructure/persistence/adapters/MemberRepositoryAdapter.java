package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpa;

    @Override
    public Member save(Member member) {
        return jpa.save(member);
    }

    @Override
    public Optional<Member> findByIdAndOrganizationIdAndStatusIn(UUID id, UUID organizationId, Collection<MemberStatus> statuses) {
        return jpa.findByIdAndOrganizationIdAndStatusIn(id, organizationId, statuses);
    }

    @Override
    public List<Member> findAllByOrganizationIdAndStatusIn(UUID organizationId, Collection<MemberStatus> statuses) {
        return jpa.findAllByOrganizationIdAndStatusIn(organizationId, statuses);
    }

    @Override
    public boolean existsByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MemberStatus status) {
        return jpa.existsByOrganizationIdAndUserIdAndStatus(organizationId, userId, status);
    }

    @Override
    public boolean existsByOrganizationIdAndEmailAndStatusIn(UUID organizationId, String email, Collection<MemberStatus> statuses) {
        return jpa.existsByOrganizationIdAndEmailAndStatusIn(organizationId, email, statuses);
    }

    @Override
    public int countByOrganizationIdAndStatus(UUID organizationId, MemberStatus status) {
        return jpa.countByOrganizationIdAndStatus(organizationId, status);
    }

    @Override
    public Optional<Member> findByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MemberStatus status) {
        return jpa.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, status);
    }
}
