/**
 * Named interface of the Workspace module — the only types other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.workspace.api.WorkspaceModuleApi} and its read-only snapshot
 * records ({@link com.kntro.reqsai.workspace.api.ProjectSnapshot},
 * {@link com.kntro.reqsai.workspace.api.GlossaryTermSnapshot}).
 * All other workspace packages are internal and must not be accessed cross-module.
 */
@org.springframework.modulith.NamedInterface("api")
package com.kntro.reqsai.workspace.api;
