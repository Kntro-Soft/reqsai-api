package com.kntro.reqsai.discovery.application.command;

import org.jspecify.annotations.Nullable;
import java.util.UUID;

public record UpdateAcceptanceCriterionCommand(
        UUID projectId,
        UUID storyId,
        UUID criterionId,
        @Nullable String scenario,
        String given,
        String when,
        String then
) {}
