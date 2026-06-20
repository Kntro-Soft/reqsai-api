package com.kntro.reqsai.iam.domain.model;

import com.kntro.reqsai.iam.domain.event.EmailVerificationRequestedEvent;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.domain.support.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time email verification token. Lives in {@code public.email_verifications}.
 * The raw token is never persisted — only its SHA-256 hex digest is stored.
 * Lifecycle: issued → consumed via {@link #markUsed(Instant)}.
 */
@Entity
@Table(name = "email_verifications", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends AggregateRoot {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "account_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public static EmailVerification issue(UUID accountId, String rawToken, Instant expiresAt) {
        EmailVerification ev = new EmailVerification(IdGenerator.newId());
        ev.tokenHash = HashUtils.sha256(rawToken);
        ev.accountId = accountId;
        ev.expiresAt = expiresAt;
        ev.registerEvent(EmailVerificationRequestedEvent.of(accountId));
        return ev;
    }

    private EmailVerification(UUID id) {
        super(id);
    }

    public boolean isValid(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

}
