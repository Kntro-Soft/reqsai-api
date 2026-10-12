package com.kntro.reqsai.codebase.application.query;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The repositories the organization's installations share, marked when already connected to the project. Without
 * a project (the one being created has none yet) none is marked.
 */
public record ListGitHubRepositoriesQuery(@Nullable UUID projectId) {
}
