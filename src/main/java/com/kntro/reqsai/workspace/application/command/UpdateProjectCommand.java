package com.kntro.reqsai.workspace.application.command;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public record UpdateProjectCommand(
        UUID organizationId,
        UUID projectId,
        String name,
        @Nullable String description,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> clientPlatforms,
        List<String> databases,
        @Nullable String architecture,
        @Nullable String domain,
        UUID requestedBy
) {}
