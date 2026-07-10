package com.kntro.reqsai.discovery.api;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Raw material for importing an external tracker issue (e.g. a Jira issue) into the Discovery backlog as a
 * user story, passed by another module (the {@code gateway}) to {@link DiscoveryStoryWritePort}.
 *
 * <p>Deliberately provider-neutral and unstructured: it carries the issue's {@code summary} (title-ish
 * line) and its {@code description} already flattened to plain text. Discovery owns the transformation
 * into a well-formed story (role/action/benefit + acceptance criteria) — via its LLM generation when
 * available, otherwise a deterministic safe mapping — so the AI stays inside the Discovery boundary.
 *
 * @param projectId   project the story will belong to (tenant-scoped)
 * @param summary     the external issue summary (never blank; used as the story title / generation seed)
 * @param description the external issue description flattened to plain text ({@code null}/blank allowed)
 * @param language    BCP-47 language tag to guide the LLM (e.g. {@code "es-PE"}); {@code null} = default
 */
public record ExternalIssueInput(
        UUID projectId,
        String summary,
        @Nullable String description,
        @Nullable String language
) {}
