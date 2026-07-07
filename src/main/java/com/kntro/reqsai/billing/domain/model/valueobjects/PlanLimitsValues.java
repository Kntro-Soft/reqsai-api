package com.kntro.reqsai.billing.domain.model.valueobjects;

/**
 * Domain representation of operational and quota limits for a given plan tier.
 */
public record PlanLimitsValues(
        int maxMembers,
        int maxProjects,
        int maxDocumentsPerProject,
        long maxTokensPerMonth,
        int maxGlossaryTermsPerProject
) {}
