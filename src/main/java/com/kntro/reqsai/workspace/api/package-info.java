/**
 * Named interface of the Workspace module — the only types other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.workspace.api.WorkspaceModuleApi} and its read-only snapshot
 * records ({@link com.kntro.reqsai.workspace.api.ProjectSnapshot},
 * {@link com.kntro.reqsai.workspace.api.ProjectDocumentSnapshot}).
 * {@link com.kntro.reqsai.workspace.api.GlossaryTermSnapshot}), plus the
 * {@link com.kntro.reqsai.workspace.api.DemoProjectSeededIntegrationEvent} other modules react to.
 * A new project's setup is exposed here too: {@link com.kntro.reqsai.workspace.api.ProjectSources},
 * {@link com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent} and the
 * {@link com.kntro.reqsai.workspace.api.ProjectSetupParticipant} and
 * {@link com.kntro.reqsai.workspace.api.ProjectSetupReporter} contracts the source modules implement and call.
 * All other workspace packages are internal and must not be accessed cross-module.
 */
@org.springframework.modulith.NamedInterface("api")
package com.kntro.reqsai.workspace.api;
