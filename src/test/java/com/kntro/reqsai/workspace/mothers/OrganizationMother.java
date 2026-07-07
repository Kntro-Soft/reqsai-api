package com.kntro.reqsai.workspace.mothers;

import com.kntro.reqsai.workspace.domain.model.Organization;

/**
 * Object Mother for {@link Organization} — named business
 * scenarios. Each returns an {@link OrganizationBuilder} so a test can customize further before
 * {@code build()}.
 */
public final class OrganizationMother {

    private OrganizationMother() {
    }

    /** A freshly created organization in {@code PENDING} status. */
    public static OrganizationBuilder pending() {
        return OrganizationBuilder.anOrganization();
    }

    /** An organization whose tenant has been provisioned ({@code ACTIVE}). */
    public static OrganizationBuilder active() {
        return OrganizationBuilder.anOrganization().active();
    }

    /** A PENDING organization with a specific slug. */
    public static OrganizationBuilder withSlug(String slug) {
        return OrganizationBuilder.anOrganization().withSlug(slug);
    }
}
