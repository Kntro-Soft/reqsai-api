/**
 * Workspace — organizations and projects bounded context.
 * <p>
 * Organization (tenant) registry and project management. Owns the {@code public.organizations}
 * registry and triggers tenant provisioning on activation. Owner: <strong>Salim</strong>.
 * <p>
 * Layers: {@code api} (named interface, public), {@code domain}, {@code application},
 * {@code infrastructure}, {@code interfaces}.
 * Depends only on the OPEN {@code shared} module and the IAM {@code ports} named interface
 * (to implement {@code OrganizationLookupPort} for cross-context JWT enrichment).
 * <p>
 * Other modules must declare {@code allowedDependencies = "workspace::api"} and may only import
 * types from {@link com.kntro.reqsai.workspace.api}.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "iam::ports"})
package com.kntro.reqsai.workspace;
