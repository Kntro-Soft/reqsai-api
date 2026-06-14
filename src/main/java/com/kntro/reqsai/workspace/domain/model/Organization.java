package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.domain.event.OrganizationCreatedEvent;
import com.kntro.reqsai.workspace.domain.valueobjects.GenerationSettings;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import com.kntro.reqsai.workspace.infrastructure.persistence.converters.SlugConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    public void updateSettings(GenerationSettings settings) {
        this.settings = Assert.notNull(settings, "settings");
    }

    public void updateLimits(PlanLimits planLimits) {
        this.planLimits = Assert.notNull(planLimits, "planLimits");
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
