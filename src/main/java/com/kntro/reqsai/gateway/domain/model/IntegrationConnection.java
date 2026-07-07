package com.kntro.reqsai.gateway.domain.model;

import com.kntro.reqsai.gateway.infrastructure.persistence.converters.EncryptedStringConverter;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Organization-scoped third-party integration connection (ADR-0022). Holds the provider, the Jira site
 * URL + account email, and the API token <strong>encrypted at rest</strong> (the {@code apiToken} field
 * is transparently encrypted/decrypted by {@link EncryptedStringConverter} into the
 * {@code secret_ciphertext} BYTEA column). The token is never exposed by any response mapper.
 */
@Entity
@Table(name = "integration_connections")
@Getter
public class IntegrationConnection extends AggregateRoot {

    private static final int SITE_URL_MAX = 500;
    private static final int EMAIL_MAX = 320;

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32, updatable = false)
    private IntegrationProviderType provider;

    @Column(name = "site_url", nullable = false, length = SITE_URL_MAX)
    private String siteUrl;

    @Column(name = "email", nullable = false, length = EMAIL_MAX)
    private String email;

    /** Plaintext in memory only; persisted encrypted via {@link EncryptedStringConverter}. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_ciphertext", nullable = false)
    private String apiToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConnectionStatus status;

    @Column(name = "last_verified_at")
    private @Nullable Instant lastVerifiedAt;

    protected IntegrationConnection() {
        super();
    }

    /**
     * Creates a Jira connection. The caller is expected to have verified the credential (via the
     * provider) before persisting; {@code verifiedAt} records that success.
     */
    public IntegrationConnection(UUID organizationId, IntegrationProviderType provider,
                                 String siteUrl, String email, String apiToken, Instant verifiedAt) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.provider = Assert.notNull(provider, "provider");
        this.siteUrl = normalizeSiteUrl(siteUrl);
        this.email = Assert.maxLength(Assert.notBlank(email, "email"), "email", EMAIL_MAX);
        this.apiToken = Assert.notBlank(apiToken, "apiToken");
        this.status = ConnectionStatus.CONNECTED;
        this.lastVerifiedAt = Assert.notNull(verifiedAt, "verifiedAt");
    }

    /** Normalizes the Jira base site URL, trimming a trailing slash so path concatenation is clean. */
    public static String normalizeSiteUrl(String siteUrl) {
        String trimmed = Assert.maxLength(Assert.notBlank(siteUrl, "siteUrl"), "siteUrl", SITE_URL_MAX);
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Marks a successful credential verification. */
    public void markVerified(Instant when) {
        this.status = ConnectionStatus.CONNECTED;
        this.lastVerifiedAt = Assert.notNull(when, "when");
    }

    /** Marks a failed credential verification without discarding the stored credential. */
    public void markDegraded() {
        this.status = ConnectionStatus.DEGRADED;
    }
}
