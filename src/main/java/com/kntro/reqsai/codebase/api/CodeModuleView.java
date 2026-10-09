package com.kntro.reqsai.codebase.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A module of the client's code as other modules see it: what it does, its capabilities and the business
 * rules it implements, with a link to it on the code host. Never carries source code.
 */
public record CodeModuleView(
        UUID id,
        String repository,
        String path,
        String name,
        String summary,
        List<String> capabilities,
        List<String> businessRules,
        List<String> endpoints,
        @Nullable String url
) {
}
