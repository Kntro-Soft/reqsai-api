package com.kntro.reqsai.iam.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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

    private static final int NAME_MAX       = 100;
    private static final int AVATAR_URL_MAX = 2048;

    @Column(name = "account_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "first_name", nullable = false, length = NAME_MAX)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = NAME_MAX)
    private String lastName;

    @Nullable
    @Column(name = "avatar_url", length = AVATAR_URL_MAX)
    private String avatarUrl;

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

    /** Updates the editable profile fields. {@code avatarUrl} may be {@code null} to clear it. */
    public void updateProfile(String firstName, String lastName, @Nullable String avatarUrl) {
        this.firstName = Assert.maxLength(Assert.notBlank(firstName, "firstName"), "firstName", NAME_MAX);
        this.lastName  = Assert.maxLength(Assert.notBlank(lastName, "lastName"), "lastName", NAME_MAX);
        this.avatarUrl = avatarUrl == null ? null : Assert.maxLength(avatarUrl.trim(), "avatarUrl", AVATAR_URL_MAX);
    }

    /** Records the last org/project the user navigated to. */
    public void updatePreferences(UserPreferences preferences) {
        this.preferences = Assert.notNull(preferences, "preferences");
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
