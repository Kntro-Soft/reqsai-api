package com.kntro.reqsai.workspace.mothers;

import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import com.kntro.reqsai.workspace.domain.model.BasePermission;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.GenerationSettings;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import net.datafaker.Faker;

import java.util.UUID;

/**
 * Fluent builder for {@link Organization} instances in tests. Uses Datafaker to generate valid data, so
 * each instance is random by default — a test only sets the fields it actually asserts on.
 */
public class OrganizationBuilder {

    private static final Faker FAKER = new Faker();

    private String name = FAKER.company().name();
    private Slug slug = Slug.of("org-" + FAKER.regexify("[a-z0-9]{8}"));
    private UUID ownerId = UUID.randomUUID();
    private LanguageCode meetingLanguage = LanguageCode.of(FAKER.options().option("es-PE", "en-US", "pt-BR"));
    private int audioRetentionDays = FAKER.number().numberBetween(0, 90);
    private PlanLimits planLimits = new PlanLimits(3, 25, 10, 100_000L, 50);
    private BasePermission memberBasePermission;
    private boolean active;

    public static OrganizationBuilder anOrganization() {
        return new OrganizationBuilder();
    }

    public OrganizationBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public OrganizationBuilder withSlug(String slug) {
        this.slug = Slug.of(slug);
        return this;
    }

    public OrganizationBuilder withOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public OrganizationBuilder withMeetingLanguage(String code) {
        this.meetingLanguage = LanguageCode.of(code);
        return this;
    }

    public OrganizationBuilder withAudioRetentionDays(int days) {
        this.audioRetentionDays = days;
        return this;
    }

    public OrganizationBuilder withPlanLimits(PlanLimits planLimits) {
        this.planLimits = planLimits;
        return this;
    }

    /** Override the organization-wide member base permission floor (defaults to {@code READ}). */
    public OrganizationBuilder withMemberBasePermission(BasePermission memberBasePermission) {
        this.memberBasePermission = memberBasePermission;
        return this;
    }

    /** Build the org already {@code ACTIVE} (provisioning done), instead of the default {@code PENDING}. */
    public OrganizationBuilder active() {
        this.active = true;
        return this;
    }

    public Organization build() {
        Organization organization = new Organization(
                name, slug, ownerId, GenerationSettings.of(meetingLanguage, audioRetentionDays), planLimits);
        if (memberBasePermission != null) {
            organization.changeMemberBasePermission(memberBasePermission);
        }
        if (active) {
            organization.activate();
        }
        return organization;
    }
}
