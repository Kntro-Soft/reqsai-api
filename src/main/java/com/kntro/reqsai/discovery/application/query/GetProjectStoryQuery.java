package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Query to retrieve a single user story by its id, scoped to a project. */
public record GetProjectStoryQuery(UUID projectId, UUID storyId) {
}
