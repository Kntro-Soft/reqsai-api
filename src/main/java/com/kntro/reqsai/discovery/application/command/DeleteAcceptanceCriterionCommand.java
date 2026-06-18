package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

public record DeleteAcceptanceCriterionCommand(
        UUID projectId,
        UUID storyId,
        UUID criterionId
) {}
