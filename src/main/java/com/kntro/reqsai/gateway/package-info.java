/**
 * Gateway — external integrations bounded context (ADR-0023).
 * <p>
 * Third-party tracker connections and story push, whose first provider is Jira Cloud. Extensible
 * provider model: credentials live at the <strong>organization</strong> level
 * ({@code IntegrationConnection}, encrypted API token); the push target (Jira project key + issue
 * type) lives at the <strong>project</strong> level ({@code ProjectIntegrationTarget}).
 * Owner: <strong>Marcelo</strong>.
 * <p>
 * Layers: {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}.
 * Depends on the OPEN {@code shared} module, the {@code workspace::api} named interface (org/project
 * authorization context — {@code @authz} + {@code Permission}) and the {@code discovery::api} named
 * interface ({@code DiscoveryStoryReadPort}, reading user stories to push).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "workspace::api", "discovery::api"})
package com.kntro.reqsai.gateway;
