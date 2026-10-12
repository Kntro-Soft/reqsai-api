/**
 * Named interface of the Workspace module for read-only dashboard figures — the
 * only insight types other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort}:
 * which active projects a caller may see, how much knowledge a project holds
 * (glossary, constraints, documents, assigned members) and the organization's
 * headcount. Figures are counted in SQL; no entity crosses this boundary.
 * <p>
 * Consumers declare {@code allowedDependencies = "workspace::insights"} in
 * their {@code @ApplicationModule}.
 */
@org.springframework.modulith.NamedInterface("insights")
package com.kntro.reqsai.workspace.insights;
