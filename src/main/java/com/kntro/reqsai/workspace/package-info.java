/**
 * Workspace — organizations and projects bounded context.
 * <p>
 * Organization (tenant) registry and project management. Owns the {@code public.organizations}
 * registry and triggers tenant provisioning on activation. Owner: <strong>Salim</strong>.
 * <p>
 * Layers: {@code api}, {@code domain}, {@code application}, {@code infrastructure}, {@code interfaces}.
 * Depends only on the OPEN {@code shared} module.
 */
@org.springframework.modulith.ApplicationModule
package com.kntro.reqsai.workspace;
