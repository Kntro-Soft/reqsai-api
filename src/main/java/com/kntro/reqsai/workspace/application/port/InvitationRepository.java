package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link Invitation} aggregate (global {@code public.invitations} registry).
 * Implemented by an adapter in {@code infrastructure}; the application layer depends only on this.
 */
public interface InvitationRepository {

    Invitation save(Invitation invitation);

    Optional<Invitation> findByTokenHash(String tokenHash);

    /** The single active (PENDING) invitation for a member, if any. */
    Optional<Invitation> findByMemberIdAndStatus(UUID memberId, InvitationStatus status);

    /** Active (PENDING) invitations addressed to an email across all organizations (Stage-2 link-on-signup). */
    List<Invitation> findAllByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);
}
