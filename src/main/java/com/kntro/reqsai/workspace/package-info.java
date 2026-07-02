/**
 * Workspace — organizations and projects bounded context.
 * <p>
 * Organization (tenant) registry and project management. Owns the {@code public.organizations}
 * registry and triggers tenant provisioning on activation. Owner: <strong>Salim</strong>.
 * <p>
 * Layers: {@code api} (named interface, public), {@code domain}, {@code application},
 * {@code infrastructure}, {@code interfaces}.
 * Depends on the OPEN {@code shared} module and two IAM named interfaces: {@code ports}
 * (to implement {@code OrganizationLookupPort} for cross-context JWT enrichment, and to consume
 * {@code EmailNotificationPort}/{@code AccountLookupPort} for invitations) and {@code api}
 * (to subscribe to {@code AccountVerifiedIntegrationEvent} for the invitation link-on-signup safety
 * net). IAM's domain events stay internal; IAM relays the verified-account event to its {@code api}
 * package so no framework annotation is needed in the domain layer.
 * <p>
 * Other modules must declare {@code allowedDependencies = "workspace::api"} and may only import
 * types from {@link com.kntro.reqsai.workspace.api}.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "iam::ports", "iam::api"})
package com.kntro.reqsai.workspace;
