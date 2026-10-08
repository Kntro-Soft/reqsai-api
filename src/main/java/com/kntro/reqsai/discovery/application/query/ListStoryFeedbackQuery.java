package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** What clients said about one story through share links, oldest first. */
public record ListStoryFeedbackQuery(UUID projectId, UUID storyId) {
}
