package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member extends AggregateRoot {

    private static final int DISPLAY_NAME_MAX = 150;

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private @Nullable UUID userId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private OrgRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MemberStatus status;

    @Column(name = "invited_by", columnDefinition = "uuid")
    private @Nullable UUID invitedBy;

    @Column(name = "invited_at")
    private @Nullable Instant invitedAt;

    protected Member() {
        super();
    }

    public Member(
            UUID organizationId,
            @Nullable UUID userId,
            String email,
            String displayName,
            OrgRole role,
            MemberStatus status,
            @Nullable UUID invitedBy,
            @Nullable Instant invitedAt) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.userId = userId;
        this.email = Email.of(email).value();
        this.displayName = Assert.maxLength(Assert.notBlank(displayName, "displayName"), "displayName", DISPLAY_NAME_MAX);
        this.role = Assert.notNull(role, "role");
        this.status = Assert.notNull(status, "status");
        this.invitedBy = invitedBy;
        this.invitedAt = invitedAt;
        validateIdentity();
    }

    private void validateIdentity() {
        if (status == MemberStatus.ACTIVE) {
            Assert.notNull(userId, "userId");
        }
        if (status == MemberStatus.PENDING) {
            Assert.notNull(invitedBy, "invitedBy");
            Assert.notNull(invitedAt, "invitedAt");
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OrgRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public @Nullable UUID getInvitedBy() {
        return invitedBy;
    }

    public @Nullable Instant getInvitedAt() {
        return invitedAt;
    }

    public void changeRole(OrgRole role) {
        this.role = Assert.notNull(role, "role");
    }

    public void deactivate() {
        this.status = MemberStatus.INACTIVE;
    }

    public void reactivate(UUID userId) {
        this.userId = Assert.notNull(userId, "userId");
        this.status = MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return this.role == OrgRole.ADMIN && this.status == MemberStatus.ACTIVE;
    }
}
