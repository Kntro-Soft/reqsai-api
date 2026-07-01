package com.kntro.reqsai.shared.application.search;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A single global-search result, produced by a bounded context's {@code search} named interface and
 * merged by the {@code search} aggregator module. A plain value snapshot — no JPA entity crosses a
 * module boundary.
 *
 * @param type      the kind of entity matched
 * @param id        the entity id
 * @param title     the primary label shown in the palette (e.g. project name, story title)
 * @param subtitle  optional secondary label (e.g. member email, org slug); {@code null} when absent
 * @param projectId owning project id for project-scoped hits (PROJECT, USER_STORY); {@code null} otherwise
 */
public record SearchHit(
        SearchHitType type,
        UUID id,
        String title,
        @Nullable String subtitle,
        @Nullable UUID projectId
) {}
