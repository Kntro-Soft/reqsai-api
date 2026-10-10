package com.kntro.reqsai.codebase.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An organization's link to a GitHub App installation: the GitHub account (a user or an organization) that
 * installed ReqsAI and chose which repositories it may read. Lives in the PUBLIC schema because GitHub's
 * webhooks name the installation, not the tenant. No credential is kept: an access token is minted from
 * the App's key each time one is needed.
 */
@Entity
@Table(name = "code_host_installations", schema = "public")
@Getter
public class CodeHostInstallation extends AggregateRoot {

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16, updatable = false)
    private CodeHostProvider provider;

    @Column(name = "installation_id", nullable = false, updatable = false)
    private long installationId;

    @Column(name = "account_login", nullable = false, length = 100)
    private String accountLogin;

    /** {@code Organization} or {@code User}, as GitHub names the account. */
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    /** {@code all} or {@code selected}: whether every repository of the account is shared. */
    @Column(name = "repository_selection", length = 16)
    private @Nullable String repositorySelection;

    /** Where the account changes which repositories ReqsAI reads, or uninstalls it. */
    @Column(name = "manage_url", length = 500)
    private @Nullable String manageUrl;

    @Column(name = "suspended_at")
    private @Nullable Instant suspendedAt;

    protected CodeHostInstallation() {
        super();
    }

    private CodeHostInstallation(UUID organizationId, long installationId) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.provider = CodeHostProvider.GITHUB;
        this.installationId = installationId;
    }

    /** Links a GitHub App installation to the organization. */
    public static CodeHostInstallation link(UUID organizationId, long installationId, String accountLogin,
                                            String accountType, @Nullable String repositorySelection,
                                            @Nullable String manageUrl, @Nullable Instant suspendedAt) {
        CodeHostInstallation installation = new CodeHostInstallation(organizationId, installationId);
        installation.refresh(accountLogin, accountType, repositorySelection, manageUrl, suspendedAt);
        return installation;
    }

    /** Takes what GitHub says about the installation now (the account may have been renamed). */
    public void refresh(String accountLogin, String accountType, @Nullable String repositorySelection,
                        @Nullable String manageUrl, @Nullable Instant suspendedAt) {
        this.accountLogin = Assert.maxLength(Assert.notBlank(accountLogin, "accountLogin"), "accountLogin", 100);
        this.accountType = Assert.maxLength(Assert.notBlank(accountType, "accountType"), "accountType", 20);
        this.repositorySelection = repositorySelection;
        this.manageUrl = manageUrl == null || manageUrl.length() > 500 ? null : manageUrl;
        this.suspendedAt = suspendedAt;
    }

    public void suspend(Instant at) {
        this.suspendedAt = Assert.notNull(at, "at");
    }

    public void unsuspend() {
        this.suspendedAt = null;
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    /** Whether the installation belongs to the given GitHub account (logins are case-insensitive). */
    public boolean isAccount(String login) {
        return login != null && accountLogin.equalsIgnoreCase(login.strip());
    }
}
