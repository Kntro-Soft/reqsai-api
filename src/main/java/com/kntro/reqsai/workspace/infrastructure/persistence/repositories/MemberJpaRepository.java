package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberJpaRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByIdAndOrganizationIdAndStatusIn(UUID id, UUID organizationId, Collection<MemberStatus> statuses);
    List<Member> findAllByOrganizationIdAndStatusIn(UUID organizationId, Collection<MemberStatus> statuses);
    boolean existsByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MemberStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM Member m
            WHERE m.organizationId = :organizationId
              AND lower(m.email) = lower(:email)
              AND m.status IN :statuses
            """)
    boolean existsByOrganizationIdAndEmailAndStatusIn(UUID organizationId, String email, Collection<MemberStatus> statuses);

    int countByOrganizationIdAndStatus(UUID organizationId, MemberStatus status);
    Optional<Member> findByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MemberStatus status);
    List<Member> findAllByUserIdAndStatus(UUID userId, MemberStatus status);
}
