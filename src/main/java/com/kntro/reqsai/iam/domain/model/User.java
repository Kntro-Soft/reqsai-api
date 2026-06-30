package com.kntro.reqsai.iam.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Aggregate root holding a user's profile, linked 1:1 to an {@link Account} by {@code accountId}
 * (reference by id, never the {@code Account} object — aggregate boundary). Lives in the global
 * {@code public.users} registry; its id is what the issued JWT carries as the {@code sub} claim and what
 * an organization stores as its {@code ownerId}.
 */
@Entity
@Table(name = "users", schema = "public")
@Getter
public class User extends AggregateRoot {

    private static final int NAME_MAX = 100;

    @Column(name = "account_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "first_name", nullable = false, length = NAME_MAX)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = NAME_MAX)
    private String lastName;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "avatar", columnDefinition = "bytea")
    private byte[] avatar;

    @Column(name = "avatar_content_type", length = 64)
    private String avatarContentType;

    @Embedded
    private UserPreferences preferences;

    protected User() {
        super();
    }

    public User(UUID accountId, String firstName, String lastName) {
        super();
        this.accountId   = Assert.notNull(accountId, "accountId");
        this.firstName   = Assert.maxLength(Assert.notBlank(firstName, "firstName"), "firstName", NAME_MAX);
        this.lastName    = Assert.maxLength(Assert.notBlank(lastName, "lastName"), "lastName", NAME_MAX);
        this.preferences = UserPreferences.empty();
    }

    /** Updates the editable profile fields. */
    public void updateProfile(String firstName, String lastName) {
        this.firstName = Assert.maxLength(Assert.notBlank(firstName, "firstName"), "firstName", NAME_MAX);
        this.lastName  = Assert.maxLength(Assert.notBlank(lastName, "lastName"), "lastName", NAME_MAX);
    }

    /** Stores the generated avatar bytes and their content type (downloaded after registration). */
    public void applyAvatar(byte[] avatar, String avatarContentType) {
        this.avatar = avatar;
        this.avatarContentType = avatarContentType;
    }

    /** Records the last org/project the user navigated to. */
    public void updatePreferences(UserPreferences preferences) {
        this.preferences = Assert.notNull(preferences, "preferences");
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
