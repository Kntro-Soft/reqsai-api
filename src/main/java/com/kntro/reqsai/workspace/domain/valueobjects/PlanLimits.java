package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Operational limits an organization runs under, set from its billing plan. Immutable value object,
 * mapped as an {@code @Embeddable} so each limit is a queryable {@code max_*} column on
 * {@code organizations}. Use {@code -1} for "unlimited".
 * <p>
 * Set from Billing, which owns the plan catalog: a new org receives the FREE limits from
 * {@code BillingModuleApi.freePlanLimits()} at creation; later plan changes call
 * {@code Organization.updateLimits(...)}.
 *
 * @param maxMembers                 max active members in the organization ({@code -1} = unlimited)
 * @param maxProjects                max projects in the organization ({@code -1} = unlimited)
 * @param maxDocumentsPerProject     max documents per project ({@code -1} = unlimited)
 * @param maxTokensPerMonth          monthly AI token quota ({@code -1} = unlimited)
 * @param maxGlossaryTermsPerProject max glossary terms per project ({@code -1} = unlimited)
 */
@Embeddable
public record PlanLimits(

        @Column(name = "max_members", nullable = false)
        int maxMembers,

        @Column(name = "max_projects", nullable = false)
        int maxProjects,

        @Column(name = "max_documents_per_project", nullable = false)
        int maxDocumentsPerProject,

        @Column(name = "max_tokens_per_month", nullable = false)
        long maxTokensPerMonth,

        @Column(name = "max_glossary_terms_per_project", nullable = false)
        int maxGlossaryTermsPerProject
) {

    public PlanLimits {
        requireValid(maxMembers, "maxMembers");
        requireValid(maxProjects, "maxProjects");
        requireValid(maxDocumentsPerProject, "maxDocumentsPerProject");
        requireValid(maxGlossaryTermsPerProject, "maxGlossaryTermsPerProject");
        if (maxTokensPerMonth < -1) {
            throw Exceptions.invalidValue("maxTokensPerMonth", "must be >= -1");
        }
    }

    private static void requireValid(int value, String field) {
        if (value < -1) {
            throw Exceptions.invalidValue(field, "must be >= -1");
        }
    }
}
