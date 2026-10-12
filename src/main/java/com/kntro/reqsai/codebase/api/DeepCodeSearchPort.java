package com.kntro.reqsai.codebase.api;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

/**
 * Searches the PURE source code of the project's connected repositories, not the stored module index: the
 * deep path of the copilot, used when the index finds nothing or is unsure, or when the analyst asks to verify
 * a suggestion in the code. Runs in the caller's tenant. The code is read from the repository archive at the
 * indexed commit and kept only for a few minutes in a temporary file; nothing is persisted.
 */
public interface DeepCodeSearchPort {

    /** Whether the project has a repository that is indexed and can be searched. */
    boolean hasReadyRepository(UUID projectId);

    /**
     * The code passages most related to {@code queryText}, within {@code budget}. {@code queryEmbedding}, when
     * given, helps pick the modules to look into first. Fails with a codebase exception when GitHub cannot be
     * reached or refuses access to every repository; returns an empty result when nothing relates.
     */
    DeepSearchResult search(UUID projectId, String queryText, float @Nullable [] queryEmbedding, Duration budget);
}
