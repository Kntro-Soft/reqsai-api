package com.kntro.reqsai.overview.application.result;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The overview of one project. A section is null when the caller's project
 * permissions do not let them read it (stories, sessions, glossary and the
 * like, integrations), so the page can leave it out.
 */
public record ProjectOverview(Instant generatedAt, int weeks, @Nullable BacklogSummary backlog,
        @Nullable DiscoverySummary discovery, @Nullable KnowledgeSummary knowledge, @Nullable CodeFootprint code) {
}
