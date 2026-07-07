package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationJpaRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByMemberIdAndStatus(UUID memberId, InvitationStatus status);

    @Query("""
            SELECT i
            FROM Invitation i
            WHERE lower(i.email) = lower(:email)
              AND i.status = :status
            """)
    List<Invitation> findAllByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);
}
