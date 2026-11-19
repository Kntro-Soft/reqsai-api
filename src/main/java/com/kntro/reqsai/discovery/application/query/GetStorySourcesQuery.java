package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Query for the fragments of meetings a story comes from, scoped to a project (US72). */
public record GetStorySourcesQuery(UUID projectId, UUID storyId) {
}
