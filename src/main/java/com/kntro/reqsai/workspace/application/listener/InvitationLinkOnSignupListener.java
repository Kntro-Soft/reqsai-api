package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.iam.domain.event.AccountVerifiedEvent;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stage-2 safety net: when an account's email is <em>verified</em> (proven), auto-accept any PENDING
 * invitations addressed to that exact email. Covers the case where an invited person signs up and
 * verifies their email without ever clicking the invitation link.
 * <p>
 * Reacts to IAM's {@link AccountVerifiedEvent} (from the {@code iam::events} named interface, which the
 * workspace module depends on). The event's email is the trust anchor — it was proven by
 * verification — so the email-match rule is naturally exact here; we still filter on it explicitly.
 * Invitations and members live in {@code public}, so no tenant context is needed. Runs after commit
 * in its own transaction (Spring Modulith).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class InvitationLinkOnSignupListener {

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final AccountLookupPort accountLookup;

    @ApplicationModuleListener
    void onAccountVerified(AccountVerifiedEvent event) {
        String verifiedEmail = event.email();
        List<Invitation> pending =
                invitations.findAllByEmailIgnoreCaseAndStatus(verifiedEmail, InvitationStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        // The member's userId is the JWT sub (User id), not the account id from the event.
        UUID userId = accountLookup.findUserIdByAccountId(event.accountId()).orElse(null);
        if (userId == null) {
            log.warn("Account {} verified but no user profile found; skipping invitation link", event.accountId());
            return;
        }

        Instant now = Instant.now();
        for (Invitation invitation : pending) {
            // Exact (case-insensitive) email match — the verified email owns the invitation.
            if (!verifiedEmail.equalsIgnoreCase(invitation.getEmail())) {
                continue;
            }
            if (!invitation.isValid(now)) {
                invitation.markExpired(now);
                invitations.save(invitation);
                continue;
            }

            members.findByIdAndOrganizationIdAndStatusIn(
                            invitation.getMemberId(), invitation.getOrganizationId(),
                            List.of(MemberStatus.PENDING, MemberStatus.ACTIVE))
                    .ifPresent(member -> {
                        if (member.getStatus() == MemberStatus.PENDING) {
                            member.reactivate(userId);
                            members.save(member);
                        }
                    });
            invitation.markAccepted(now);
            invitations.save(invitation);
            log.info("Auto-accepted invitation {} on email verification (org {})",
                    invitation.getId(), invitation.getOrganizationId());
        }
    }
}
