package com.kntro.reqsai.codebase.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to the client's indexed code for other modules (the requirements copilot). Runs in the
 * caller's tenant.
 */
public interface CodebaseModuleApi {

    /**
     * The modules of the project's code most related to {@code queryText}: nearest by embedding when one is
     * given, by shared words otherwise. Empty when the project has no indexed code or nothing relates.
     */
    List<CodeModuleView> findRelevantModules(UUID projectId, float @Nullable [] queryEmbedding, String queryText,
                                             int topK);

    /** The overview of the project's indexed repositories, or empty when none is ready. */
    Optional<CodeOverview> findOverview(UUID projectId);
}
