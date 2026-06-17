package com.kntro.reqsai.discovery.application.command;

import org.jspecify.annotations.Nullable;
import java.util.UUID;

public record AddAcceptanceCriterionCommand(
        UUID projectId,
        UUID storyId,
        @Nullable String scenario,
        String given,
        String when,
        String then
) {}
