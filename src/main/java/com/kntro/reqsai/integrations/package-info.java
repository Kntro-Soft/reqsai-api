/**
 * Integrations — third-party tracker connections and story push bounded context (ADR-0022).
 * <p>
 * Extensible provider model whose first implementation is Jira Cloud. Credentials live at the
 * <strong>organization</strong> level ({@code IntegrationConnection}, encrypted API token); the push
 * target (Jira project key + issue type) lives at the <strong>project</strong> level
 * ({@code ProjectIntegrationTarget}). Owners: <strong>Jhosepmyr</strong>.
 * <p>
 * Layers: {@code api}, {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}.
 * Depends on the OPEN {@code shared} module, the {@code workspace::api} named interface (org/project
 * authorization context) and the {@code discovery::api} named interface (reading user stories to push).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "workspace::api", "discovery::api"})
package com.kntro.reqsai.integrations;
