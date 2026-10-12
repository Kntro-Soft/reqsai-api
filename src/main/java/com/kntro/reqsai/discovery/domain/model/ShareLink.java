package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A link that lets a client without an account review a project's stories, approve them and leave
 * comments (US50), or follow a discovery session as a spectator or reviewer. Lives in the global {@code public.share_links} registry because the
 * visitor is anonymous: the link itself names the organization and project it opens. Only the hash
 * of the raw token is stored; the raw value is shown to the analyst once, when the link is created.
 */
@Entity
@Table(name = "share_links", schema = "public")
@Getter
public class ShareLink extends AggregateRoot {

    /** Shortest and longest validity the analyst may pick. */
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 90;

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    /** The discovery session the link follows; {@code null} for a link that shares the project's backlog. */
    @Column(name = "session_id", columnDefinition = "uuid", updatable = false)
    private @Nullable UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access", nullable = false, length = 16, updatable = false)
    private ShareAccess access;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    protected ShareLink() {
        super();
    }

    private ShareLink(UUID organizationId, UUID projectId, @Nullable UUID sessionId, ShareAccess access,
                      String tokenHash, Instant expiresAt) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.projectId = Assert.notNull(projectId, "projectId");
        this.sessionId = sessionId;
        this.access = Assert.notNull(access, "access");
        this.tokenHash = Assert.notBlank(tokenHash, "tokenHash");
        this.expiresAt = Assert.notNull(expiresAt, "expiresAt");
    }

    /** Issues a backlog link valid for {@code days} days from {@code now}, storing only the hash of {@code rawToken}. */
    public static ShareLink issue(UUID organizationId, UUID projectId, String rawToken, int days, Instant now) {
        return build(organizationId, projectId, null, ShareAccess.REVIEW, rawToken, days, now);
    }

    /** Issues a link that follows one discovery session, as a spectator or as a reviewer. */
    public static ShareLink issueForSession(UUID organizationId, UUID projectId, UUID sessionId, ShareAccess access,
                                            String rawToken, int days, Instant now) {
        return build(organizationId, projectId, Assert.notNull(sessionId, "sessionId"), access, rawToken, days, now);
    }

    private static ShareLink build(UUID organizationId, UUID projectId, @Nullable UUID sessionId, ShareAccess access,
                                   String rawToken, int days, Instant now) {
        Assert.isTrue(days >= MIN_DAYS && days <= MAX_DAYS, "days",
                "must be between " + MIN_DAYS + " and " + MAX_DAYS);
        return new ShareLink(organizationId, projectId, sessionId, access,
                HashUtils.sha256(Assert.notBlank(rawToken, "token")),
                Assert.notNull(now, "now").plus(Duration.ofDays(days)));
    }

    /** True for a link that follows a discovery session rather than sharing the backlog. */
    public boolean isSessionLink() {
        return sessionId != null;
    }

    /** True when the visitor may decide (accept, dismiss, approve, reject), not only look. */
    public boolean canReview() {
        return access == ShareAccess.REVIEW;
    }

    /** True while the link can be opened: not revoked and not expired. */
    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /** Stops the link from working. Revoking twice keeps the first revocation time. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = Assert.notNull(now, "now");
        }
    }
}
