package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/** Evaluates the quality of a story now and stores the result as its latest assessment. */
public record EvaluateStoryQualityCommand(UUID projectId, UUID storyId) {
}
