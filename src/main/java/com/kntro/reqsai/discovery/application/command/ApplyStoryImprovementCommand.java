package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.application.port.StoryDraft;

import java.util.UUID;

/**
 * Intent to apply an improved story the analyst reviewed: replaces the title, role, action, benefit and acceptance
 * criteria of the story with {@code proposal} and evaluates its quality again. Scoped to a project.
 *
 * @param proposal the text to apply, with every {@code [[...]]} token already replaced by the analyst
 */
public record ApplyStoryImprovementCommand(UUID projectId, UUID storyId, StoryDraft proposal) {
}
