package com.kntro.reqsai.discovery.api;

import org.jspecify.annotations.Nullable;

/**
 * Read-only projection of a user story's acceptance criterion exposed by Discovery via
 * {@link DiscoveryStoryReadPort}. Given/When/Then plus an optional scenario label. No JPA entity.
 */
public record AcceptanceCriterionView(
        @Nullable String scenario,
        String given,
        String when,
        String then
) {}
