package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A read-only link that lets a client without an account review a project's stories, approve them
 * and leave comments (US50). Lives in the global {@code public.share_links} registry because the
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

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    protected ShareLink() {
        super();
    }

    private ShareLink(UUID organizationId, UUID projectId, String tokenHash, Instant expiresAt) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.projectId = Assert.notNull(projectId, "projectId");
        this.tokenHash = Assert.notBlank(tokenHash, "tokenHash");
        this.expiresAt = Assert.notNull(expiresAt, "expiresAt");
    }

    /** Issues a link valid for {@code days} days from {@code now}, storing only the hash of {@code rawToken}. */
    public static ShareLink issue(UUID organizationId, UUID projectId, String rawToken, int days, Instant now) {
        Assert.isTrue(days >= MIN_DAYS && days <= MAX_DAYS, "days",
                "must be between " + MIN_DAYS + " and " + MAX_DAYS);
        return new ShareLink(organizationId, projectId, HashUtils.sha256(Assert.notBlank(rawToken, "token")),
                Assert.notNull(now, "now").plus(Duration.ofDays(days)));
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
