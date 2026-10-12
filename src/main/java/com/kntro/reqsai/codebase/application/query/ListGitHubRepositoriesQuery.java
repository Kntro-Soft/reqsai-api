package com.kntro.reqsai.codebase.application.query;

import java.util.UUID;

/** The repositories the organization's installations share, marked when already connected to the project. */
public record ListGitHubRepositoriesQuery(UUID projectId) {
}
