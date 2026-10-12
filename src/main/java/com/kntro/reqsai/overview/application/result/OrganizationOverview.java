package com.kntro.reqsai.overview.application.result;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.workspace.insights.MemberHeadcount;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The overview of an organization, covering the projects the caller may see.
 *
 * @param projects
 *            active projects the caller may see
 * @param members
 *            the people of the organization; null unless the caller is an owner
 *            or admin
 * @param topProjects
 *            the busiest projects, most active first
 */
public record OrganizationOverview(Instant generatedAt, int weeks, int projects, @Nullable MemberHeadcount members,
        BacklogSummary backlog, DiscoverySummary discovery, List<ProjectActivitySummary> topProjects,
        CodeFootprint code) {
}
