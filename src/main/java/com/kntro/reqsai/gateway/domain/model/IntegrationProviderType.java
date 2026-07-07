package com.kntro.reqsai.gateway.domain.model;

/**
 * Supported third-party integration providers. Only {@code JIRA} exists today; the value is stored on
 * {@code IntegrationConnection} and drives provider-adapter selection (ADR-0022), so adding a provider
 * (e.g. Azure DevOps) is additive.
 */
public enum IntegrationProviderType {
    JIRA
}
