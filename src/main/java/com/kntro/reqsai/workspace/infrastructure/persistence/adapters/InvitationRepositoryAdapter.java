package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.InvitationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InvitationRepositoryAdapter implements InvitationRepository {

    private final InvitationJpaRepository jpa;

    @Override
    public Invitation save(Invitation invitation) {
        return jpa.save(invitation);
    }

    @Override
    public Optional<Invitation> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }

    @Override
    public Optional<Invitation> findByMemberIdAndStatus(UUID memberId, InvitationStatus status) {
        return jpa.findByMemberIdAndStatus(memberId, status);
    }

    @Override
    public List<Invitation> findAllByEmailIgnoreCaseAndStatus(String email, InvitationStatus status) {
        return jpa.findAllByEmailIgnoreCaseAndStatus(email, status);
    }
}
