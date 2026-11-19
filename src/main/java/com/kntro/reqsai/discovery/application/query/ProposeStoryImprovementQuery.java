package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Asks the AI for an improved version of a story. Scoped to a project: a story outside it is a 404. */
public record ProposeStoryImprovementQuery(UUID projectId, UUID storyId) {
}
