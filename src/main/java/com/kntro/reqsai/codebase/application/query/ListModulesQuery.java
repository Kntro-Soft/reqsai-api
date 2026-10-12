package com.kntro.reqsai.codebase.application.query;

import java.util.UUID;

public record ListModulesQuery(UUID projectId, UUID repositoryId) {
}
