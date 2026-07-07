package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import com.kntro.reqsai.workspace.domain.event.OrganizationCreatedEvent;
import com.kntro.reqsai.workspace.domain.valueobjects.GenerationSettings;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import com.kntro.reqsai.workspace.infrastructure.persistence.converters.SlugConverter;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Aggregate root of the multi-tenant account — the root of tenancy. Lives in the global
 * {@code public.organizations} registry (not in a tenant schema); the {@code TenantSchemaResolver} maps
 * its {@code slug} to the {@code tenant_<slug>} schema.
 * <p>
 * Created {@code PENDING}; the application layer provisions the tenant schema and then calls
 * {@link #activate()}, which flips it to {@code ACTIVE} and raises {@link OrganizationCreatedEvent}.
 * Value objects are kept as typed fields: single-value ones ({@code slug}) via a {@code varchar}
 * converter; structured ones ({@code settings}, {@code planLimits}) as {@code @Embedded} so each field
 * is a real, queryable column (e.g. filter orgs by {@code meeting_language} or over their project limit).
 */
@Entity
@Table(name = "organizations", schema = "public")
@Getter
public class Organization extends AggregateRoot {

    private static final int NAME_MAX = 150;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Convert(converter = SlugConverter.class)
    @Column(name = "slug", nullable = false, unique = true, length = 50, updatable = false)
    private Slug slug;

    @Column(name = "owner_id", columnDefinition = "uuid", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrgStatus status;

    @Embedded
    private GenerationSettings settings;

    @Embedded
    private PlanLimits planLimits;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "avatar", columnDefinition = "bytea")
    private byte[] avatar;

    @Column(name = "avatar_content_type", length = 64)
    private String avatarContentType;

    protected Organization() {
        super();
    }

    public Organization(String name, Slug slug, UUID ownerId,
                        GenerationSettings settings, PlanLimits planLimits) {
        super();
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
        this.slug = Assert.notNull(slug, "slug");
        this.ownerId = Assert.notNull(ownerId, "ownerId");
        this.status = OrgStatus.PENDING;
        this.settings = Assert.notNull(settings, "settings");
        this.planLimits = Assert.notNull(planLimits, "planLimits");
    }

    public void rename(String name) {
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
    }

    /** Reassigns ownership to another user id. The previous owner is demoted to a member elsewhere. */
    public void transferOwnership(UUID newOwnerId) {
        this.ownerId = Assert.notNull(newOwnerId, "newOwnerId");
    }

    public void updateSettings(GenerationSettings settings) {
        this.settings = Assert.notNull(settings, "settings");
    }

    /**
     * Applies a partial update: each argument is optional and a {@code null} leaves the corresponding
     * field unchanged. When present, {@code name} is validated and the settings are rebuilt keeping the
     * untouched fields. {@code meetingLanguage} is the raw BCP-47 string (parsed into a {@link LanguageCode}).
     */
    public void applyPatch(String name, String meetingLanguage, Integer audioRetentionDays) {
        if (name != null) {
            this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
        }
        if (meetingLanguage != null || audioRetentionDays != null) {
            LanguageCode language = meetingLanguage != null ? LanguageCode.of(meetingLanguage) : null;
            this.settings = this.settings.withChanges(language, audioRetentionDays);
        }
    }

    public void updateLimits(PlanLimits planLimits) {
        this.planLimits = Assert.notNull(planLimits, "planLimits");
    }

    /** Stores the generated avatar bytes and their content type (downloaded after creation). */
    public void applyAvatar(byte[] avatar, String avatarContentType) {
        this.avatar = avatar;
        this.avatarContentType = avatarContentType;
    }

    public void activate() {
        this.status = OrgStatus.ACTIVE;
        registerEvent(OrganizationCreatedEvent.of(getId(), ownerId, slug.value()));
    }

    public void deactivate() {
        this.status = OrgStatus.INACTIVE;
    }

    public void reactivate() {
        this.status = OrgStatus.ACTIVE;
    }

    public void delete() {
        this.status = OrgStatus.DELETED;
    }
}
