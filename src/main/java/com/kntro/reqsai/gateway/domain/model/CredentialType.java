package com.kntro.reqsai.gateway.domain.model;

/**
 * How an {@link IntegrationConnection} authenticates against its provider (ADR-0023).
 * <ul>
 *   <li>{@code API_TOKEN} — Jira basic auth: {@code Authorization: Basic base64(email:token)} against
 *       {@code https://{site}/rest/api/3}. The {@code email} + encrypted {@code secret_ciphertext} are
 *       populated; the OAuth columns are null.</li>
 *   <li>{@code OAUTH2} — Jira OAuth 2.0 (3LO): {@code Authorization: Bearer {access}} against
 *       {@code https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3}. The {@code cloud_id} + encrypted
 *       OAuth refresh/access tokens are populated; {@code email} + {@code secret_ciphertext} are null.</li>
 * </ul>
 * Exactly one credential shape is populated per value (application-enforced by the domain factory).
 */
public enum CredentialType {

    /** Jira API token + account email over basic auth (the original flow). */
    API_TOKEN,

    /** Jira OAuth 2.0 (3LO) refresh/access tokens over bearer auth. */
    OAUTH2
}
