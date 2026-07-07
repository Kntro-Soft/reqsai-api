package com.kntro.reqsai.billing.api;

/**
 * Snapshot DTO of operational plan limits that can cross context boundaries.
 */
public record PlanLimitsSnapshot(
        int maxMembers,
        int maxProjects,
        int maxDocumentsPerProject,
        long maxTokensPerMonth,
        int maxGlossaryTermsPerProject
) {}
