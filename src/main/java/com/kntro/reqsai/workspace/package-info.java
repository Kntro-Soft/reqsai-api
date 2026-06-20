/**
 * Workspace — organizations and projects bounded context.
 * <p>
 * Organization (tenant) registry and project management. Owns the {@code public.organizations}
 * registry and triggers tenant provisioning on activation. Owner: <strong>Salim</strong>.
 * <p>
 * Layers: {@code api} (named interface, public), {@code domain}, {@code application},
 * {@code infrastructure}, {@code interfaces}.
 * Depends only on the OPEN {@code shared} module.
 * <p>
 * Other modules must declare {@code allowedDependencies = "workspace::api"} and may only import
 * types from {@link com.kntro.reqsai.workspace.api}.
 */
@org.springframework.modulith.ApplicationModule
package com.kntro.reqsai.workspace;
