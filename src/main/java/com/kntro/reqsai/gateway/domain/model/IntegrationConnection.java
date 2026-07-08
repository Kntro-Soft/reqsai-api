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
 * Organization-scoped third-party integration connection (ADR-0023). Holds the provider, the Jira site
 * URL, and one of two credential shapes selected by {@link #credentialType}:
 * <ul>
 *   <li>{@link CredentialType#API_TOKEN} — account {@code email} + the API token
 *       <strong>encrypted at rest</strong> ({@code apiToken} → {@code secret_ciphertext} BYTEA).</li>
 *   <li>{@link CredentialType#OAUTH2} — the Atlassian {@code cloudId} + the OAuth refresh/access tokens
 *       <strong>encrypted at rest</strong> ({@code oauth_refresh_ciphertext} / {@code oauth_access_ciphertext})
 *       plus the access-token expiry.</li>
 * </ul>
 * All secrets are transparently encrypted/decrypted by {@link EncryptedStringConverter} and are never
 * exposed by any response mapper.
 */
@Entity
@Table(name = "integration_connections")
@Getter
public class IntegrationConnection extends AggregateRoot {

    private static final int SITE_URL_MAX = 500;
    private static final int EMAIL_MAX = 320;
    private static final int CLOUD_ID_MAX = 64;

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32, updatable = false)
    private IntegrationProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 32, updatable = false)
    private CredentialType credentialType;

    @Column(name = "site_url", nullable = false, length = SITE_URL_MAX)
    private String siteUrl;

    /** Populated for {@link CredentialType#API_TOKEN}; null for OAuth. */
    @Column(name = "email", length = EMAIL_MAX)
    private @Nullable String email;

    /** Plaintext in memory only; persisted encrypted via {@link EncryptedStringConverter}. API_TOKEN only. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_ciphertext")
    private @Nullable String apiToken;

    /** Atlassian cloud id (site id) for OAuth calls. Populated for {@link CredentialType#OAUTH2}. */
    @Column(name = "cloud_id", length = CLOUD_ID_MAX)
    private @Nullable String cloudId;

    /** OAuth refresh token (plaintext in memory only; persisted encrypted). OAUTH2 only. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "oauth_refresh_ciphertext")
    private @Nullable String oauthRefreshToken;

    /** OAuth access token (plaintext in memory only; persisted encrypted). OAUTH2 only. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "oauth_access_ciphertext")
    private @Nullable String oauthAccessToken;

    /** When the current OAuth access token expires; used to decide when to refresh. OAUTH2 only. */
    @Column(name = "oauth_access_expires_at")
    private @Nullable Instant oauthAccessExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConnectionStatus status;

    @Column(name = "last_verified_at")
    private @Nullable Instant lastVerifiedAt;

    protected IntegrationConnection() {
        super();
    }

    /**
     * Creates an API-token ({@link CredentialType#API_TOKEN}) Jira connection. The caller is expected to
     * have verified the credential (via the provider) before persisting; {@code verifiedAt} records that
     * success.
     */
    public IntegrationConnection(UUID organizationId, IntegrationProviderType provider,
                                 String siteUrl, String email, String apiToken, Instant verifiedAt) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.provider = Assert.notNull(provider, "provider");
        this.credentialType = CredentialType.API_TOKEN;
        this.siteUrl = normalizeSiteUrl(siteUrl);
        this.email = Assert.maxLength(Assert.notBlank(email, "email"), "email", EMAIL_MAX);
        this.apiToken = Assert.notBlank(apiToken, "apiToken");
        this.status = ConnectionStatus.CONNECTED;
        this.lastVerifiedAt = Assert.notNull(verifiedAt, "verifiedAt");
    }

    /**
     * Creates an OAuth 2.0 (3LO) ({@link CredentialType#OAUTH2}) Jira connection from a completed token
     * exchange. {@code siteUrl} is the discovered accessible-resource URL, {@code cloudId} its site id.
     * The refresh token is required (obtained via the {@code offline_access} scope); the access token +
     * its expiry are cached so the first call need not refresh.
     */
    public static IntegrationConnection oauth(UUID organizationId, IntegrationProviderType provider,
                                              String siteUrl, String cloudId, String refreshToken,
                                              String accessToken, Instant accessExpiresAt, Instant verifiedAt) {
        IntegrationConnection c = new IntegrationConnection();
        c.organizationId = Assert.notNull(organizationId, "organizationId");
        c.provider = Assert.notNull(provider, "provider");
        c.credentialType = CredentialType.OAUTH2;
        c.siteUrl = normalizeSiteUrl(siteUrl);
        c.cloudId = Assert.maxLength(Assert.notBlank(cloudId, "cloudId"), "cloudId", CLOUD_ID_MAX);
        c.oauthRefreshToken = Assert.notBlank(refreshToken, "refreshToken");
        c.oauthAccessToken = Assert.notBlank(accessToken, "accessToken");
        c.oauthAccessExpiresAt = Assert.notNull(accessExpiresAt, "accessExpiresAt");
        c.status = ConnectionStatus.CONNECTED;
        c.lastVerifiedAt = Assert.notNull(verifiedAt, "verifiedAt");
        return c;
    }

    /**
     * Applies rotated OAuth tokens after a refresh. Atlassian may return a new (rotated) refresh token; if
     * so it is persisted, otherwise the existing refresh token is kept. The fresh access token + expiry
     * replace the cached pair. No-op semantics for API-token connections is prevented by the caller.
     */
    public void applyRefreshedTokens(@Nullable String rotatedRefreshToken, String accessToken,
                                     Instant accessExpiresAt) {
        Assert.isTrue(credentialType == CredentialType.OAUTH2, "credentialType",
                "applyRefreshedTokens requires an OAUTH2 credential");
        if (rotatedRefreshToken != null && !rotatedRefreshToken.isBlank()) {
            this.oauthRefreshToken = rotatedRefreshToken;
        }
        this.oauthAccessToken = Assert.notBlank(accessToken, "accessToken");
        this.oauthAccessExpiresAt = Assert.notNull(accessExpiresAt, "accessExpiresAt");
        this.status = ConnectionStatus.CONNECTED;
    }

    /** True when the OAuth access token is missing, expired, or within {@code skew} of expiring. */
    public boolean oauthAccessExpiredWithin(java.time.Duration skew, Instant now) {
        if (credentialType != CredentialType.OAUTH2) {
            return false;
        }
        return oauthAccessToken == null || oauthAccessExpiresAt == null
                || !oauthAccessExpiresAt.isAfter(now.plus(skew));
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
